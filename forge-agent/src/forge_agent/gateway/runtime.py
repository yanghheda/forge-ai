"""LangGraph Runtime 与 SQLite Checkpoint 的受控边界。"""

from __future__ import annotations

import sqlite3
from datetime import UTC, datetime
from pathlib import Path
from threading import Lock
from typing import Any, Literal, Protocol, TypedDict

from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.config import get_config
from langgraph.graph import END, START, StateGraph
from pydantic import BaseModel, ConfigDict, Field

from forge_agent.gateway.tool_transport import ToolCallRecord, ToolExecution, ToolTransport


def _camel(value: str) -> str:
    first, *remaining = value.split("_")
    return first + "".join(part.capitalize() for part in remaining)


class ManifestSubject(BaseModel):
    """由 Backend 认证后的运行主体。"""

    model_config = ConfigDict(alias_generator=_camel, populate_by_name=True)
    user_id: int = Field(gt=0)


class ManifestScope(BaseModel):
    """Backend 已裁剪的公司与业务资源范围。"""

    model_config = ConfigDict(alias_generator=_camel, populate_by_name=True)
    organization_id: int = Field(gt=0)
    work_item_id: int | None = Field(default=None, gt=0)


class ManifestPolicy(BaseModel):
    """限制本次图执行预算，但不表达业务授权。"""

    model_config = ConfigDict(alias_generator=_camel, populate_by_name=True)
    medium_confirmation: Literal["ASK", "ALLOW", "DENY"]
    max_tool_calls: int = Field(ge=0, le=20)
    token_budget: int = Field(gt=0)


class ResourceReference(BaseModel):
    """指向 Backend 权威资源版本，不复制资源正文。"""

    type: str = Field(min_length=1)
    id: str = Field(min_length=1)
    version: int = Field(ge=0)


class ContextManifest(BaseModel):
    """Backend 生成的最小运行上下文清单。"""

    model_config = ConfigDict(alias_generator=_camel, populate_by_name=True)
    run_id: str = Field(min_length=1)
    subject: ManifestSubject
    scope: ManifestScope
    skill: Literal["PRODUCT", "UX", "DEVELOPER", "QA", "RELEASE"]
    effective_tool_names: list[str]
    policy: ManifestPolicy
    resource_refs: list[ResourceReference]
    expires_at: datetime
    prompt_version: str | None = None
    context_template: str | None = None

    def require_active(self, now: datetime | None = None) -> None:
        """拒绝无时区或过期 Manifest，避免恢复时沿用陈旧上下文。"""

        if self.expires_at.tzinfo is None:
            raise ValueError("manifest expiresAt must include timezone")
        if self.expires_at <= (now or datetime.now(UTC)):
            raise ValueError("manifest expired")


class RunStart(BaseModel):
    """内部启动端点交给 Runtime 的不可变输入。"""

    manifest: ContextManifest
    message: str = Field(min_length=1, max_length=10_000)


class RunResult(BaseModel):
    """最小图完成后返回给 Backend Gateway 的结构化结果；序列化键保持 snake_case。"""

    status: Literal["SUCCEEDED", "WAITING_APPROVAL"]
    plan: list[str]
    answer: str
    tool_calls: list[ToolCallRecord] = Field(default_factory=list)
    state_version: int


class ToolSelection(BaseModel):
    """模型一次选定的 Tool 调用意图；仅是意图，授权与风险由后续防线裁决。"""

    tool_name: str = Field(min_length=1)
    arguments: dict[str, Any] = Field(default_factory=dict)


class LanguageModel(Protocol):
    """图节点依赖的窄模型接口。"""

    def create_plan(self, skill: str, message: str) -> list[str]: ...

    def select_tool(
        self, skill: str, message: str, executed_tool_names: list[str]
    ) -> ToolSelection | None: ...

    def finalize(self, skill: str, resource_count: int, tool_call_count: int) -> str: ...


class FakeLanguageModel:
    """不访问网络的确定性模型，用于验证图和恢复语义。"""

    def __init__(self, *, fail_finalize_once: bool = False) -> None:
        self.plan_calls = 0
        self.select_calls = 0
        self.finalize_calls = 0
        self._fail_finalize_once = fail_finalize_once

    def create_plan(self, skill: str, message: str) -> list[str]:
        self.plan_calls += 1
        return [f"理解 {skill} 请求", "整理受控上下文", "形成可回溯结果"]

    def select_tool(
        self, skill: str, message: str, executed_tool_names: list[str]
    ) -> ToolSelection | None:
        """消息含“创建需求”且尚未执行过时选择 create_requirement，否则结束。"""

        self.select_calls += 1
        if "创建需求" in message and "create_requirement" not in executed_tool_names:
            return ToolSelection(tool_name="create_requirement", arguments={"title": "Fake 需求"})
        return None

    def finalize(self, skill: str, resource_count: int, tool_call_count: int) -> str:
        self.finalize_calls += 1
        if self._fail_finalize_once:
            self._fail_finalize_once = False
            raise RuntimeError("injected finalize failure")
        return (
            f"Fake LLM completed {skill} plan with {resource_count} resource reference(s) "
            f"and {tool_call_count} tool call(s)."
        )


class AgentState(TypedDict, total=False):
    """Checkpoint 中仅保存恢复所需的最小状态。"""

    run_id: str
    skill: str
    message: str
    effective_tools: list[str]
    max_tool_calls: int
    resource_count: int
    plan: list[str]
    answer: str
    status: str
    state_version: int
    tool_calls: list[dict[str, Any]]
    pending_selection: dict[str, Any]
    pending_execution: dict[str, Any]


class RuntimeGateway(Protocol):
    """Agent Run 执行器的可替换边界。"""

    def start(self, request: RunStart, run_token: str) -> RunResult: ...


class LangGraphRuntimeGateway:
    """使用真实 StateGraph 和 SQLiteSaver 执行最小有界图。"""

    def __init__(
        self, checkpoint_path: Path, model: LanguageModel, transport: ToolTransport
    ) -> None:
        checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
        self._connection = sqlite3.connect(checkpoint_path, check_same_thread=False)
        self._checkpointer = SqliteSaver(self._connection)
        self._model = model
        self._transport = transport
        self._lock = Lock()
        graph = StateGraph(AgentState)
        graph.add_node("validate_context", self._validate_context)
        graph.add_node("create_plan", self._create_plan)
        graph.add_node("select_tool", self._select_tool)
        graph.add_node("guard_tool", self._guard_tool)
        graph.add_node("execute_tool", self._execute_tool)
        graph.add_node("observe_tool", self._observe_tool)
        graph.add_node("finalize", self._finalize)
        graph.add_edge(START, "validate_context")
        graph.add_edge("validate_context", "create_plan")
        graph.add_edge("create_plan", "select_tool")
        graph.add_conditional_edges(
            "select_tool",
            self._after_selection,
            {"guard": "guard_tool", "finalize": "finalize"},
        )
        graph.add_conditional_edges(
            "guard_tool",
            self._after_guard,
            {"execute": "execute_tool", "observe": "observe_tool"},
        )
        graph.add_conditional_edges(
            "execute_tool",
            self._after_execution,
            {"pause": END, "observe": "observe_tool"},
        )
        graph.add_edge("observe_tool", "select_tool")
        graph.add_edge("finalize", END)
        self._graph = graph.compile(checkpointer=self._checkpointer)

    def start(self, request: RunStart, run_token: str) -> RunResult:
        request.manifest.require_active()
        config = {"configurable": {"thread_id": request.manifest.run_id, "run_token": run_token}}
        with self._lock:
            snapshot = self._graph.get_state(config)
            if snapshot.values.get("status") == "SUCCEEDED":
                return self._result(snapshot.values)
            if snapshot.values.get("status") == "WAITING_APPROVAL":
                self._graph.update_state(
                    config,
                    {"status": "RUNNING", "pending_execution": None},
                    as_node="guard_tool",
                )
                state = self._graph.invoke(None, config)
                return self._result(state)
            if snapshot.values:
                state = self._graph.invoke(None, config)
            else:
                state = self._graph.invoke(
                    {
                        "run_id": request.manifest.run_id,
                        "skill": request.manifest.skill,
                        "message": request.message,
                        "effective_tools": list(request.manifest.effective_tool_names),
                        "max_tool_calls": request.manifest.policy.max_tool_calls,
                        "resource_count": len(request.manifest.resource_refs),
                        "tool_calls": [],
                        "state_version": 0,
                    },
                    config,
                )
            return self._result(state)

    def _validate_context(self, state: AgentState) -> AgentState:
        return {"state_version": state.get("state_version", 0) + 1}

    def _create_plan(self, state: AgentState) -> AgentState:
        return {
            "plan": self._model.create_plan(state["skill"], state["message"]),
            "state_version": state["state_version"] + 1,
        }

    def _select_tool(self, state: AgentState) -> AgentState:
        """模型依据消息与已执行观察选择下一个 Tool；无选择则进入收尾。"""

        executed = [str(call.get("toolName", "")) for call in state.get("tool_calls", [])]
        selection = self._model.select_tool(state["skill"], state["message"], executed)
        if selection is None:
            return {"pending_selection": None, "state_version": state["state_version"] + 1}
        return {
            "pending_selection": {
                "toolName": selection.tool_name,
                "arguments": selection.arguments,
            },
            "state_version": state["state_version"] + 1,
        }

    def _guard_tool(self, state: AgentState) -> AgentState:
        """Agent 侧预检：Manifest 白名单与调用预算；权威裁决在 forge-server。"""

        selection = state["pending_selection"]
        tool_name = str(selection["toolName"])
        tool_calls = state.get("tool_calls", [])
        if tool_name not in state.get("effective_tools", []):
            return {
                "pending_selection": None,
                "pending_execution": {
                    "toolName": tool_name,
                    "toolCallId": self._next_tool_call_id(state),
                    "status": "REJECTED",
                    "errorCode": "TOOL_NOT_IN_MANIFEST",
                    "errorMessage": "Tool is not in manifest effective tool names",
                },
                "state_version": state["state_version"] + 1,
            }
        if len(tool_calls) >= state["max_tool_calls"]:
            return {
                "pending_selection": None,
                "pending_execution": {
                    "toolName": tool_name,
                    "toolCallId": self._next_tool_call_id(state),
                    "status": "REJECTED",
                    "errorCode": "TOOL_CALL_BUDGET_EXCEEDED",
                    "errorMessage": "Run policy max tool calls reached",
                },
                "state_version": state["state_version"] + 1,
            }
        return {"state_version": state["state_version"] + 1}

    def _execute_tool(self, state: AgentState) -> AgentState:
        """携带 run 凭据调用 Server 内部端点；传输失败归一为 FAILED 信封。"""

        selection = state["pending_selection"]
        tool_name = str(selection["toolName"])
        tool_call_id = self._next_tool_call_id(state)
        run_token = str(get_config()["configurable"].get("run_token", ""))
        try:
            execution = self._transport.execute(
                tool_name, tool_call_id, dict(selection["arguments"]), run_token
            )
        except Exception as exception:
            execution = ToolExecution(
                status="FAILED",
                tool_name=tool_name,
                tool_call_id=tool_call_id,
                error_code="TRANSPORT_ERROR",
                error_message=str(exception),
            )
        return {
            "pending_selection": (selection if execution.status == "WAITING_APPROVAL" else None),
            "pending_execution": {
                "toolName": execution.tool_name,
                "toolCallId": execution.tool_call_id,
                "status": execution.status,
                "replayed": execution.replayed,
                "errorCode": execution.error_code,
                "errorMessage": execution.error_message,
                "result": execution.result,
                "approvalId": execution.approval_id,
            },
            "status": (
                "WAITING_APPROVAL"
                if execution.status == "WAITING_APPROVAL"
                else state.get("status", "RUNNING")
            ),
            "state_version": state["state_version"] + 1,
        }

    def _observe_tool(self, state: AgentState) -> AgentState:
        """把执行信封写入可回溯观察记录，供模型下一轮选择与最终结果引用。"""

        execution = state["pending_execution"]
        tool_calls = list(state.get("tool_calls", []))
        tool_calls.append(dict(execution))
        return {
            "tool_calls": tool_calls,
            "pending_execution": None,
            "state_version": state["state_version"] + 1,
        }

    def _finalize(self, state: AgentState) -> AgentState:
        return {
            "answer": self._model.finalize(
                state["skill"], state["resource_count"], len(state.get("tool_calls", []))
            ),
            "status": "SUCCEEDED",
            "state_version": state["state_version"] + 1,
        }

    @staticmethod
    def _after_selection(state: AgentState) -> str:
        return "guard" if state.get("pending_selection") else "finalize"

    @staticmethod
    def _after_guard(state: AgentState) -> str:
        return "observe" if state.get("pending_execution") else "execute"

    @staticmethod
    def _after_execution(state: AgentState) -> str:
        return "pause" if state.get("status") == "WAITING_APPROVAL" else "observe"

    @staticmethod
    def _next_tool_call_id(state: AgentState) -> str:
        # 已观察调用属于 checkpoint 持久状态，以其数量派生序号可在节点重试时保持稳定，
        # 又能确保下一次调用不会复用上一个幂等键。
        return f"call-{len(state.get('tool_calls', [])) + 1}"

    def _result(self, state: dict[str, object]) -> RunResult:
        tool_calls = [
            ToolCallRecord.model_validate(dict(call)) for call in state.get("tool_calls", [])
        ]
        return RunResult(
            status=str(state["status"]),
            plan=list(state["plan"]),
            answer=str(state.get("answer", "Waiting for approval")),
            tool_calls=tool_calls,
            state_version=int(state["state_version"]),
        )
