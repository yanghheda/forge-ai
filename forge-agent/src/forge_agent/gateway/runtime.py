"""LangGraph Runtime 与 SQLite Checkpoint 的受控边界。"""

from __future__ import annotations

import logging
import sqlite3
from collections.abc import Iterable
from datetime import UTC, datetime
from pathlib import Path
from threading import Event, Lock
from typing import Any, Literal, Protocol, TypedDict

from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.config import get_config
from langgraph.graph import END, START, StateGraph
from pydantic import BaseModel, ConfigDict, Field

from forge_agent.gateway.tool_transport import ToolCallRecord, ToolExecution, ToolTransport

LOGGER = logging.getLogger(__name__)


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


class ManifestToolDefinition(BaseModel):
    """Backend 按 Skill 裁剪后提供给模型的 Tool 生成契约。"""

    model_config = ConfigDict(alias_generator=_camel, populate_by_name=True)
    name: str = Field(min_length=1)
    description: str = Field(min_length=1)
    input_schema: dict[str, Any]


class ContextManifest(BaseModel):
    """Backend 生成的最小运行上下文清单。"""

    model_config = ConfigDict(alias_generator=_camel, populate_by_name=True)
    run_id: str = Field(min_length=1)
    subject: ManifestSubject
    scope: ManifestScope
    skill: Literal["PRODUCT", "UX", "DEVELOPER", "QA", "RELEASE"]
    effective_tool_names: list[str]
    tool_definitions: list[ManifestToolDefinition] = Field(default_factory=list)
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
        self,
        skill: str,
        message: str,
        work_item_id: int | None,
        tool_definitions: list[dict[str, Any]],
        tool_calls: list[dict[str, Any]],
    ) -> ToolSelection | None: ...

    def finalize(
        self,
        skill: str,
        message: str,
        resource_count: int,
        tool_calls: list[dict[str, Any]],
    ) -> str: ...

    def stream_final(
        self,
        skill: str,
        message: str,
        resource_count: int,
        tool_calls: list[dict[str, Any]],
    ) -> Iterable[str]: ...


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
        self,
        skill: str,
        message: str,
        work_item_id: int | None,
        tool_definitions: list[dict[str, Any]],
        tool_calls: list[dict[str, Any]],
    ) -> ToolSelection | None:
        """消息同时含“创建”和“需求”且尚未执行过时选择 create_requirement。"""

        self.select_calls += 1
        executed_tool_names = [str(call.get("toolName", "")) for call in tool_calls]
        if any(call.get("status") != "SUCCEEDED" for call in tool_calls):
            return None
        creates_requirement = ("创建" in message and "需求" in message) or (
            skill == "PRODUCT" and work_item_id is None and "新建" in message
        )
        if creates_requirement and "create_requirement" not in executed_tool_names:
            return ToolSelection(
                tool_name="create_requirement",
                arguments={
                    "title": "Fake 需求",
                    "description": "用于验证 ForgeAI Fake Agent 黄金流程的确定性需求。",
                },
            )
        if work_item_id is None:
            return None
        if skill == "RELEASE" and "创建" in message and "发布" in message:
            release_call = next(
                (call for call in tool_calls if call.get("toolName") == "create_release"), None
            )
            if release_call is None:
                return self._asset_selection(skill, message, work_item_id)
            if "预检" in message and "run_release_precheck" not in executed_tool_names:
                release_result = release_call.get("result")
                if isinstance(release_result, dict) and release_result.get("id") is not None:
                    return ToolSelection(
                        tool_name="run_release_precheck",
                        arguments={"releaseId": int(release_result["id"])},
                    )
            return None
        asset_selection = self._asset_selection(skill, message, work_item_id)
        if asset_selection is not None:
            if asset_selection.tool_name not in executed_tool_names:
                return asset_selection
            return None
        if skill == "PRODUCT" and "PRD" in message.upper():
            if "create_prd_document" not in executed_tool_names:
                return ToolSelection(
                    tool_name="create_prd_document",
                    arguments={
                        "requirementId": work_item_id,
                        "title": "Fake PRD",
                        "contentMarkdown": (
                            "# 1 背景与目标\n\n"
                            "为当前需求创建可编辑的产品需求文档。\n\n"
                            "## 2 验收标准\n\n"
                            "- PRD 正文已保存为初始版本"
                        ),
                    },
                )
            return None
        if skill == "UX" and "创建" in message and "任务" in message:
            if "create_ux_task" not in executed_tool_names:
                return ToolSelection(
                    tool_name="create_ux_task",
                    arguments={"requirementId": work_item_id, "title": "Fake UX Task"},
                )
            return None
        if skill == "UX" and "创建" in message and ("文档" in message or "SPEC" in message.upper()):
            if "create_ux_document" not in executed_tool_names:
                return ToolSelection(
                    tool_name="create_ux_document",
                    arguments={"requirementId": work_item_id, "title": "Fake UX Spec"},
                )
            return None
        if "推进" in message or "提交" in message or "评审" in message:
            work_item_call = next(
                (call for call in tool_calls if call.get("toolName") == "get_work_item"), None
            )
            if work_item_call is None:
                return ToolSelection(
                    tool_name="get_work_item", arguments={"workItemId": work_item_id}
                )
            if "advance_requirement" in executed_tool_names:
                return None
            result = work_item_call.get("result")
            if not isinstance(result, dict):
                return None
            transition = self._transition_for(skill, str(result.get("status", "")))
            if transition is None:
                return None
            arguments: dict[str, Any] = {
                "requirementId": work_item_id,
                "action": transition,
                "expectedVersion": int(result["version"]),
            }
            if transition == "SUBMIT_UX_REVIEW":
                arguments["checklist"] = [
                    "userFlow",
                    "pageList",
                    "keyInteraction",
                    "exceptionState",
                ]
            return ToolSelection(tool_name="advance_requirement", arguments=arguments)
        return None

    def stream_final(
        self,
        skill: str,
        message: str,
        resource_count: int,
        tool_calls: list[dict[str, Any]],
    ) -> Iterable[str]:
        """Fake 模型也按多个片段输出，用于覆盖真实流式链路。"""

        answer = self.finalize(skill, message, resource_count, tool_calls)
        midpoint = max(1, len(answer) // 2)
        yield answer[:midpoint]
        if midpoint < len(answer):
            yield answer[midpoint:]

    @staticmethod
    def _asset_selection(skill: str, message: str, requirement_id: int) -> ToolSelection | None:
        if skill == "DEVELOPER" and "技术设计" in message:
            return ToolSelection(
                tool_name="create_tech_design",
                arguments={"requirementId": requirement_id, "title": "Fake Tech Design"},
            )
        if skill == "DEVELOPER" and "开发任务" in message:
            return ToolSelection(
                tool_name="create_dev_task",
                arguments={"requirementId": requirement_id, "title": "Fake Dev Task"},
            )
        if skill == "QA" and "测试用例" in message:
            return ToolSelection(
                tool_name="create_test_case",
                arguments={
                    "requirementId": requirement_id,
                    "title": "Fake Test Case",
                    "steps": ["执行黄金流程操作"],
                    "expectedResult": "操作符合验收预期",
                    "priority": "P1",
                },
            )
        if skill == "QA" and ("缺陷" in message or "BUG" in message.upper()):
            return ToolSelection(
                tool_name="create_bug",
                arguments={
                    "requirementId": requirement_id,
                    "title": "Fake Bug",
                    "severity": "MAJOR",
                    "reproductionSteps": ["执行黄金流程操作"],
                    "expectedResult": "操作成功",
                    "actualResult": "操作失败",
                },
            )
        if skill == "RELEASE" and "创建" in message and "发布" in message:
            return ToolSelection(
                tool_name="create_release",
                arguments={
                    "versionName": "fake-golden-release",
                    "environment": "staging-simulated",
                    "requirementIds": [requirement_id],
                    "approvalTtlMinutes": 60,
                },
            )
        return None

    @staticmethod
    def _transition_for(skill: str, status: str) -> str | None:
        transitions = {
            ("PRODUCT", "DRAFT"): "SUBMIT_PRODUCT_REVIEW",
            ("PRODUCT", "PRODUCT_REVIEW"): "APPROVE_PRODUCT_REVIEW",
            ("UX", "UX_IN_PROGRESS"): "SUBMIT_UX_REVIEW",
            ("UX", "UX_REVIEW"): "APPROVE_UX_REVIEW",
            ("DEVELOPER", "IN_DEVELOPMENT"): "SUBMIT_FOR_QA",
            ("QA", "READY_FOR_QA"): "START_QA",
            ("QA", "IN_QA"): "QA_PASS",
        }
        return transitions.get((skill, status))

    def finalize(
        self,
        skill: str,
        message: str,
        resource_count: int,
        tool_calls: list[dict[str, Any]],
    ) -> str:
        self.finalize_calls += 1
        if self._fail_finalize_once:
            self._fail_finalize_once = False
            raise RuntimeError("injected finalize failure")
        failed_call = next(
            (call for call in reversed(tool_calls) if call.get("status") != "SUCCEEDED"),
            None,
        )
        if failed_call is not None:
            tool_name = str(failed_call.get("toolName", "unknown_tool"))
            error_code = str(failed_call.get("errorCode") or failed_call.get("status") or "FAILED")
            error_message = failed_call.get("errorMessage")
            detail = f"：{error_message}" if error_message else ""
            return (
                f"工具 {tool_name} 执行失败（{error_code}）{detail}；"
                "业务操作未完成，请查看 Tool Trace 并修复后重试。"
            )
        requests_requirement_creation = ("创建" in message and "需求" in message) or (
            skill == "PRODUCT" and "新建" in message
        )
        if requests_requirement_creation and not tool_calls:
            return "未执行 create_requirement Tool，需求尚未创建。"
        return (
            f"Fake LLM completed {skill} plan with {resource_count} resource reference(s) "
            f"and {len(tool_calls)} tool call(s)."
        )


class AgentState(TypedDict, total=False):
    """Checkpoint 中仅保存恢复所需的最小状态。"""

    run_id: str
    skill: str
    message: str
    effective_tools: list[str]
    tool_definitions: list[dict[str, Any]]
    max_tool_calls: int
    resource_count: int
    work_item_id: int | None
    plan: list[str]
    answer: str
    status: str
    state_version: int
    tool_calls: list[dict[str, Any]]
    pending_selection: dict[str, Any]
    pending_execution: dict[str, Any]


class AnswerStream(Protocol):
    """把最终回答片段发送给 Backend 权威事件流。"""

    def publish(self, run_id: str, delta: str, run_token: str) -> None: ...

    def publish_reasoning(self, run_id: str, delta: str, run_token: str) -> None: ...


class RuntimeGateway(Protocol):
    """Agent Run 执行器的可替换边界。"""

    def start(self, request: RunStart, run_token: str) -> RunResult: ...

    def cancel(self, run_id: str) -> None: ...


class RunCancelled(RuntimeError):
    """Run 已收到用户取消信号。"""


class LangGraphRuntimeGateway:
    """使用真实 StateGraph 和 SQLiteSaver 执行最小有界图。"""

    def __init__(
        self,
        checkpoint_path: Path,
        model: LanguageModel,
        transport: ToolTransport,
        answer_stream: AnswerStream | None = None,
    ) -> None:
        checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
        self._connection = sqlite3.connect(checkpoint_path, check_same_thread=False)
        self._checkpointer = SqliteSaver(self._connection)
        self._model = model
        self._transport = transport
        self._answer_stream = answer_stream
        self._lock = Lock()
        self._cancellations: dict[str, Event] = {}
        graph = StateGraph(AgentState)
        graph.add_node("validate_context", self._validate_context)
        graph.add_node("create_plan", self._create_plan)
        graph.add_node("select_tool", self._select_tool)
        graph.add_node("guard_tool", self._guard_tool)
        graph.add_node("execute_tool", self._execute_tool)
        graph.add_node("observe_tool", self._observe_tool)
        graph.add_node("complete_action", self._complete_action)
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
        graph.add_conditional_edges(
            "observe_tool",
            self._after_observation,
            {
                "select": "select_tool",
                "complete_action": "complete_action",
                "finalize": "finalize",
            },
        )
        graph.add_edge("complete_action", END)
        graph.add_edge("finalize", END)
        self._graph = graph.compile(checkpointer=self._checkpointer)

    def start(self, request: RunStart, run_token: str) -> RunResult:
        request.manifest.require_active()
        cancellation = self._cancellations.setdefault(request.manifest.run_id, Event())
        config = {
            # 每次 Tool 调用会经过 select/guard/execute/observe 四个节点；
            # LangGraph 默认 25 步不足以承载 Manifest 允许的最多 20 次调用。
            "recursion_limit": max(25, request.manifest.policy.max_tool_calls * 4 + 10),
            "configurable": {
                "thread_id": request.manifest.run_id,
                "run_token": run_token,
                "cancellation": cancellation,
            },
        }
        with self._lock:
            if cancellation.is_set():
                raise RunCancelled()
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
                        "tool_definitions": [
                            definition.model_dump(by_alias=True)
                            for definition in request.manifest.tool_definitions
                        ],
                        "max_tool_calls": request.manifest.policy.max_tool_calls,
                        "resource_count": len(request.manifest.resource_refs),
                        "work_item_id": request.manifest.scope.work_item_id,
                        "tool_calls": [],
                        "state_version": 0,
                    },
                    config,
                )
            return self._result(state)

    def cancel(self, run_id: str) -> None:
        self._cancellations.setdefault(run_id, Event()).set()

    @staticmethod
    def _require_not_cancelled() -> None:
        cancellation = get_config()["configurable"].get("cancellation")
        if isinstance(cancellation, Event) and cancellation.is_set():
            raise RunCancelled()

    def _validate_context(self, state: AgentState) -> AgentState:
        return {"state_version": state.get("state_version", 0) + 1}

    def _create_plan(self, state: AgentState) -> AgentState:
        self._require_not_cancelled()
        plan = self._model.create_plan(state["skill"], state["message"])
        run_token = str(get_config()["configurable"].get("run_token", ""))
        if self._answer_stream is not None:
            for item in plan:
                try:
                    self._answer_stream.publish_reasoning(state["run_id"], item, run_token)
                except Exception:
                    # 执行思路属于可恢复的展示事件，发布失败不改变 Run 的业务结果。
                    LOGGER.warning(
                        "Reasoning summary publish failed; continuing run: run_id=%s",
                        state["run_id"],
                        exc_info=True,
                    )
        return {
            "plan": plan,
            "state_version": state["state_version"] + 1,
        }

    def _select_tool(self, state: AgentState) -> AgentState:
        """模型依据消息与已执行观察选择下一个 Tool；无选择则进入收尾。"""

        self._require_not_cancelled()
        selection = self._model.select_tool(
            state["skill"],
            state["message"],
            state.get("work_item_id"),
            state.get("tool_definitions", []),
            state.get("tool_calls", []),
        )
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

        self._require_not_cancelled()
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

    def _complete_action(self, state: AgentState) -> AgentState:
        """关键写操作成功后依据 Tool 事实直接收尾，避免后置模型阻塞终态。"""

        last_call = state.get("tool_calls", [])[-1]
        tool_name = last_call.get("toolName")
        result = last_call.get("result")
        result = result if isinstance(result, dict) else {}
        if tool_name == "create_prd_document":
            details: list[str] = []
            if result.get("id") is not None:
                details.append(f"文档 {result['id']}")
            if result.get("currentVersionNo") is not None:
                details.append(f"版本 {result['currentVersionNo']}")
            suffix = f"（{'，'.join(details)}）" if details else ""
            answer = f"PRD 正文已保存{suffix}。"
        else:
            details = []
            if result.get("status") is not None:
                details.append(f"状态 {result['status']}")
            if result.get("version") is not None:
                details.append(f"版本 {result['version']}")
            suffix = f"（{'，'.join(details)}）" if details else ""
            answer = f"当前阶段已推进{suffix}。"
        run_token = str(get_config()["configurable"].get("run_token", ""))
        if self._answer_stream is not None:
            try:
                self._answer_stream.publish(state["run_id"], answer, run_token)
            except Exception:
                # 完整回答仍会随 Run 终态提交，实时事件失败不能覆盖已成功的 PRD 写入。
                LOGGER.warning(
                    "Action completion publish failed; continuing with final result: run_id=%s",
                    state["run_id"],
                    exc_info=True,
                )
        return {
            "answer": answer,
            "status": "SUCCEEDED",
            "state_version": state["state_version"] + 1,
        }

    def _finalize(self, state: AgentState) -> AgentState:
        chunks: list[str] = []
        run_token = str(get_config()["configurable"].get("run_token", ""))
        stream_final = getattr(self._model, "stream_final", None)
        stream = (
            stream_final(
                state["skill"],
                state["message"],
                state["resource_count"],
                state.get("tool_calls", []),
            )
            if stream_final is not None
            else [
                self._model.finalize(
                    state["skill"],
                    state["message"],
                    state["resource_count"],
                    state.get("tool_calls", []),
                )
            ]
        )
        for delta in stream:
            self._require_not_cancelled()
            if not delta:
                continue
            chunks.append(delta)
            if self._answer_stream is not None:
                try:
                    self._answer_stream.publish(state["run_id"], delta, run_token)
                except Exception:
                    # 正文增量只用于实时展示；完整回答会随 Run 终态再次提交，
                    # 因此回放通道故障不能抹掉已经成功的业务 Tool 结果。
                    LOGGER.warning(
                        "Answer delta publish failed; continuing with final result: run_id=%s",
                        state["run_id"],
                        exc_info=True,
                    )
        return {
            "answer": "".join(chunks),
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
    def _after_observation(state: AgentState) -> str:
        """关键写操作完成即收尾，不再让模型重复决策同一副作用。"""

        tool_calls = state.get("tool_calls", [])
        if tool_calls:
            last_call = tool_calls[-1]
            if last_call.get("status") == "SUCCEEDED" and last_call.get("toolName") in {
                "create_prd_document",
                "advance_requirement",
            }:
                return "complete_action"
            if last_call.get("status") != "SUCCEEDED":
                return "finalize"
        return "select"

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
