"""forge-agent 回调 forge-server 内部 Tool 执行端点的受控 Transport。"""

from __future__ import annotations

from typing import Any, Literal, Protocol

import httpx
from pydantic import BaseModel, ConfigDict, Field


class ToolExecution(BaseModel):
    """forge-server 返回的单次 Tool 执行信封。"""

    status: Literal["SUCCEEDED", "PENDING_CONFIRMATION", "WAITING_APPROVAL", "REJECTED", "FAILED"]
    tool_name: str
    tool_call_id: str
    replayed: bool = False
    result: dict[str, Any] | None = None
    error_code: str | None = None
    error_message: str | None = None
    approval_id: str | None = None


class ToolTransport(Protocol):
    """图 execute 节点依赖的窄 Transport 接口。"""

    def execute(
        self,
        tool_name: str,
        tool_call_id: str,
        arguments: dict[str, Any],
        run_token: str,
    ) -> ToolExecution: ...


class ToolTransportFailure(RuntimeError):
    """网络层或协议层失败；转换为 FAILED 信封而不是让图崩溃。"""


class ServerToolTransport:
    """httpx 实现；只访问契约 backend_mapping 声明的内部端点。"""

    def __init__(self, base_url: str, timeout_seconds: float = 10.0) -> None:
        self._client = httpx.Client(base_url=base_url.rstrip("/"), timeout=timeout_seconds)

    def execute(
        self,
        tool_name: str,
        tool_call_id: str,
        arguments: dict[str, Any],
        run_token: str,
    ) -> ToolExecution:
        """携带 run-scoped 凭据调用内部端点；非 2xx 响应归一为 FAILED。"""

        try:
            response = self._client.post(
                f"/internal/v1/tools/{tool_name}:execute",
                json={"toolCallId": tool_call_id, "arguments": arguments},
                headers={"Authorization": f"Bearer {run_token}"},
            )
        except httpx.HTTPError as exception:
            raise ToolTransportFailure(f"tool transport request failed: {tool_name}") from exception
        if response.status_code == httpx.codes.UNAUTHORIZED:
            raise ToolTransportFailure(f"run credential rejected by server: {tool_name}")
        if response.status_code != httpx.codes.OK:
            return ToolExecution(
                status="FAILED",
                tool_name=tool_name,
                tool_call_id=tool_call_id,
                error_code="SERVER_ERROR",
                error_message=f"unexpected server status {response.status_code}",
            )
        try:
            return _snake_execution(response.json(), tool_name, tool_call_id)
        except ValueError as exception:
            raise ToolTransportFailure(
                f"tool execution payload is not valid: {tool_name}"
            ) from exception


def _snake_execution(payload: dict[str, Any], tool_name: str, tool_call_id: str) -> ToolExecution:
    """Server 端 camelCase 信封转为 Agent 内部 snake_case 模型。"""

    result = payload.get("result")
    return ToolExecution(
        status=payload.get("status", "FAILED"),
        tool_name=str(payload.get("toolName", tool_name)),
        tool_call_id=str(payload.get("toolCallId", tool_call_id)),
        replayed=bool(payload.get("replayed", False)),
        result=result if isinstance(result, dict) else None,
        error_code=payload.get("errorCode"),
        error_message=payload.get("errorMessage"),
        approval_id=payload.get("approvalId"),
    )


class StaticToolTransport:
    """测试用确定性 Transport；按注册顺序返回预置执行结果。"""

    def __init__(self, executions: list[ToolExecution]) -> None:
        self._executions = list(executions)
        self.requests: list[dict[str, Any]] = []

    def execute(
        self,
        tool_name: str,
        tool_call_id: str,
        arguments: dict[str, Any],
        run_token: str,
    ) -> ToolExecution:
        """记录请求并弹出下一个预置结果；耗尽即失败避免静默通过。"""

        self.requests.append(
            {
                "tool_name": tool_name,
                "tool_call_id": tool_call_id,
                "arguments": arguments,
                "run_token": run_token,
            }
        )
        if not self._executions:
            raise ToolTransportFailure(f"no preset execution for {tool_name}")
        return self._executions.pop(0)


class ToolCallRecord(BaseModel):
    """图中一次 Tool 调用的可回溯观察记录。"""

    model_config = ConfigDict(populate_by_name=True)

    tool_name: str = Field(alias="toolName")
    tool_call_id: str = Field(alias="toolCallId")
    status: str
    replayed: bool = False
    error_code: str | None = Field(default=None, alias="errorCode")
    error_message: str | None = Field(default=None, alias="errorMessage")
    result: dict[str, Any] | None = None
    approval_id: str | None = Field(default=None, alias="approvalId")
