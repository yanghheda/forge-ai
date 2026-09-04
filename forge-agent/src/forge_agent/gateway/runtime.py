"""LangGraph Runtime 与 SQLite Checkpoint 的受控边界。"""

from __future__ import annotations

import sqlite3
from datetime import UTC, datetime
from pathlib import Path
from threading import Lock
from typing import Literal, Protocol, TypedDict

from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.graph import END, START, StateGraph
from pydantic import BaseModel, ConfigDict, Field


def _camel(value: str) -> str:
    first, *remaining = value.split("_")
    return first + "".join(part.capitalize() for part in remaining)


class ManifestSubject(BaseModel):
    """由 Backend 认证后的运行主体。"""

    model_config = ConfigDict(alias_generator=_camel, populate_by_name=True)
    user_id: int = Field(gt=0)


class ManifestScope(BaseModel):
    """Backend 已裁剪的租户与业务资源范围。"""

    model_config = ConfigDict(alias_generator=_camel, populate_by_name=True)
    workspace_id: int = Field(gt=0)
    project_id: int = Field(gt=0)
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
    skill: Literal["PRODUCT", "UX"]
    effective_tool_names: list[str]
    policy: ManifestPolicy
    resource_refs: list[ResourceReference]
    expires_at: datetime

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
    """最小图完成后返回给 Backend Gateway 的结构化结果。"""

    status: Literal["SUCCEEDED"]
    plan: list[str]
    answer: str
    state_version: int


class LanguageModel(Protocol):
    """图节点依赖的窄模型接口。"""

    def create_plan(self, skill: str, message: str) -> list[str]: ...

    def finalize(self, skill: str, resource_count: int) -> str: ...


class FakeLanguageModel:
    """不访问网络的确定性模型，用于验证图和恢复语义。"""

    def __init__(self, *, fail_finalize_once: bool = False) -> None:
        self.plan_calls = 0
        self.finalize_calls = 0
        self._fail_finalize_once = fail_finalize_once

    def create_plan(self, skill: str, message: str) -> list[str]:
        self.plan_calls += 1
        return [f"理解 {skill} 请求", "整理受控上下文", "形成可回溯结果"]

    def finalize(self, skill: str, resource_count: int) -> str:
        self.finalize_calls += 1
        if self._fail_finalize_once:
            self._fail_finalize_once = False
            raise RuntimeError("injected finalize failure")
        return f"Fake LLM completed {skill} plan with {resource_count} resource reference(s)."


class AgentState(TypedDict, total=False):
    """Checkpoint 中仅保存恢复所需的最小状态。"""

    run_id: str
    skill: str
    message: str
    resource_count: int
    plan: list[str]
    answer: str
    status: str
    state_version: int


class RuntimeGateway(Protocol):
    """Agent Run 执行器的可替换边界。"""

    def start(self, request: RunStart) -> RunResult:
        """按 Run ID 幂等启动或恢复图。"""

        ...


class LangGraphRuntimeGateway:
    """使用真实 StateGraph 和 SQLiteSaver 执行最小有界图。"""

    def __init__(self, checkpoint_path: Path, model: LanguageModel) -> None:
        checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
        self._connection = sqlite3.connect(checkpoint_path, check_same_thread=False)
        self._checkpointer = SqliteSaver(self._connection)
        self._model = model
        self._lock = Lock()
        graph = StateGraph(AgentState)
        graph.add_node("validate_context", self._validate_context)
        graph.add_node("create_plan", self._create_plan)
        graph.add_node("finalize", self._finalize)
        graph.add_edge(START, "validate_context")
        graph.add_edge("validate_context", "create_plan")
        graph.add_edge("create_plan", "finalize")
        graph.add_edge("finalize", END)
        self._graph = graph.compile(checkpointer=self._checkpointer)

    def start(self, request: RunStart) -> RunResult:
        request.manifest.require_active()
        config = {"configurable": {"thread_id": request.manifest.run_id}}
        with self._lock:
            snapshot = self._graph.get_state(config)
            if snapshot.values.get("status") == "SUCCEEDED":
                return self._result(snapshot.values)
            if snapshot.values:
                state = self._graph.invoke(None, config)
            else:
                state = self._graph.invoke(
                    {
                        "run_id": request.manifest.run_id,
                        "skill": request.manifest.skill,
                        "message": request.message,
                        "resource_count": len(request.manifest.resource_refs),
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

    def _finalize(self, state: AgentState) -> AgentState:
        return {
            "answer": self._model.finalize(state["skill"], state["resource_count"]),
            "status": "SUCCEEDED",
            "state_version": state["state_version"] + 1,
        }

    def _result(self, state: dict[str, object]) -> RunResult:
        return RunResult(
            status="SUCCEEDED",
            plan=list(state["plan"]),
            answer=str(state["answer"]),
            state_version=int(state["state_version"]),
        )
