"""阿里云百炼千问模型的受控 OpenAI 兼容适配器。"""

from __future__ import annotations

import json
import time
from collections.abc import Iterable
from typing import Any

import httpx
from pydantic import BaseModel, Field, ValidationError

from forge_agent.gateway.runtime import ThinkingSink, ToolSelection


class ModelGatewayError(RuntimeError):
    """模型 Provider 失败，并携带可跨进程传递的稳定错误语义。"""

    def __init__(self, error_code: str, *, retryable: bool) -> None:
        super().__init__(error_code)
        self.error_code = error_code
        self.retryable = retryable


class _PlanResponse(BaseModel):
    """计划节点要求模型返回的结构。"""

    steps: list[str] = Field(min_length=1, max_length=12)


class _ToolResponse(BaseModel):
    """Tool 选择节点要求模型返回的结构。"""

    tool_name: str | None = Field(default=None, alias="toolName")
    arguments: dict[str, Any] = Field(default_factory=dict)


class _AnswerResponse(BaseModel):
    """收尾节点要求模型返回的结构。"""

    answer: str = Field(min_length=1)


class QwenLanguageModel:
    """通过百炼 Chat Completions 调用千问，并只接受结构化节点输出。"""

    def __init__(
        self,
        *,
        api_key: str,
        base_url: str,
        model: str,
        timeout_seconds: float = 30.0,
        max_attempts: int = 2,
        retry_backoff_seconds: float = 0.25,
        client: httpx.Client | None = None,
    ) -> None:
        self._model = model
        self._endpoint = f"{base_url.rstrip('/')}/chat/completions"
        self._client = client or httpx.Client(
            timeout=timeout_seconds,
            headers={"Authorization": f"Bearer {api_key}"},
        )
        self._max_attempts = max_attempts
        self._retry_backoff_seconds = retry_backoff_seconds
        self._thinking_sink: ThinkingSink | None = None

    def set_thinking_sink(self, sink: ThinkingSink | None) -> None:
        """由 Runtime 在 Run 期间注入思考片段接收器，Run 结束后清理。"""

        self._thinking_sink = sink

    def _emit_thinking(self, reasoning: object) -> None:
        """把 Provider 思考片段交给 sink；缺失或非文本时静默跳过，不影响解析语义。"""

        sink = self._thinking_sink
        if sink is None or not isinstance(reasoning, str) or not reasoning:
            return
        sink(reasoning)

    def create_plan(
        self,
        skill: str,
        message: str,
        guidance: str | None = None,
        effective_tools: list[str] | None = None,
    ) -> list[str]:
        payload = self._complete_json(
            "你是 ForgeAI 的计划节点。只返回 JSON："
            '{"steps":["步骤"]}。步骤必须简短、可执行；计划不代表授权。'
            "计划中的每一步都必须能由当前阶段允许的 Tool 完成，不得规划人工审批动作。",
            (
                f"Skill: {skill}\n"
                f"阶段能力说明: {guidance or '（未声明）'}\n"
                f"本阶段允许的 Tool: {json.dumps(effective_tools or [], ensure_ascii=False)}\n"
                f"用户指令: {message}"
            ),
        )
        return _PlanResponse.model_validate(payload).steps

    def select_tool(
        self,
        skill: str,
        message: str,
        work_item_id: int | None,
        tool_definitions: list[dict[str, Any]],
        tool_calls: list[dict[str, Any]],
        guidance: str | None = None,
    ) -> ToolSelection | None:
        payload = self._complete_json(
            "你是 ForgeAI 的 Tool 选择节点。只返回 JSON："
            '{"toolName":"工具名或 null","arguments":{}}。'
            "不要把自然语言结果伪装成 Tool 执行结果；不得猜测资源 ID 或 expectedVersion，"
            "缺少时先调用读取 Tool；必须依据结构化观察决定下一步；没有必要调用时返回 null。"
            "调用 create_requirement 时 description 必填，必须根据用户指令生成至少 20 字的具体"
            "需求说明，包含业务背景、目标或用户价值；不得使用空字符串、标题复述或通用占位文案。"
            "\n\n关键约束：调用 create_prd_document 时，contentMarkdown 参数必须包含完整生成"
            "的 PRD 正文（背景与目标、用户旅程、功能范围、异常流程、可验证验收标准等），"
            "不得只填章节标题、生成计划或占位文本；必须在该次调用内直接输出完整 Markdown。"
            "当用户要求创建或填充 PRD，且结构化 Tool 观察中已有成功的 "
            "search_documents、尚无成功的 create_prd_document 时，不得再次调用 "
            "search_documents，必须选择 create_prd_document 并生成完整正文。"
            "当用户要求推进当前阶段，且结构化 Tool 观察中已有成功的 get_work_item、"
            "尚无成功的 advance_requirement 时，不得再次调用 get_work_item，必须选择 "
            "advance_requirement，并使用观察结果中的最新 version 作为 expectedVersion。"
            "\n\n阶段边界：只选择阶段能力说明允许的动作；人工专属动作（会签票、审核与驳回、"
            "验收、部署审批、缺陷取消）没有对应 Tool，不得尝试绕过。"
            "当观察结果含 guardHints（缺失项）时，必须先用自然语言向用户说明缺失什么，"
            "不得重复选择同一动作；当观察结果含 availableActions 时，只能从中选择动作。"
            "当 Tool 因强制确认进入 WAITING_APPROVAL 时，说明该动作已提交人工确认，"
            "不得再次选择同一 Tool。",
            (
                f"Skill: {skill}\n"
                f"阶段能力说明: {guidance or '（未声明）'}\n"
                f"用户指令: {message}\n"
                f"当前 Requirement ID: {work_item_id}\n"
                f"允许 Tool 契约: {json.dumps(tool_definitions, ensure_ascii=False)}\n"
                f"结构化 Tool 观察: {json.dumps(tool_calls, ensure_ascii=False)}"
            ),
        )
        parsed = _ToolResponse.model_validate(payload)
        if parsed.tool_name is None:
            return None
        return ToolSelection(tool_name=parsed.tool_name, arguments=parsed.arguments)

    def finalize(
        self,
        skill: str,
        message: str,
        resource_count: int,
        tool_calls: list[dict[str, Any]],
        guidance: str | None = None,
    ) -> str:
        payload = self._complete_json(
            "你是 ForgeAI 的结果收尾节点。只返回 JSON："
            '{"answer":"简洁结果"}。不得声称未由 Tool 结果证实的业务写入已成功。'
            "若存在 WAITING_APPROVAL 的 Tool，必须说明该动作已提交人工确认、等待审批后生效。",
            (
                f"Skill: {skill}\n阶段能力说明: {guidance or '（未声明）'}\n"
                f"用户指令: {message}\n"
                f"可回溯资源引用数: {resource_count}\n"
                f"结构化 Tool 观察: {json.dumps(tool_calls, ensure_ascii=False)}"
            ),
        )
        return _AnswerResponse.model_validate(payload).answer

    def stream_final(
        self,
        skill: str,
        message: str,
        resource_count: int,
        tool_calls: list[dict[str, Any]],
        guidance: str | None = None,
    ) -> Iterable[str]:
        """使用 Provider 的 SSE Chat Completion，逐片段输出最终自然语言答案。"""

        system_prompt = (
            "你是 ForgeAI 的结果收尾节点。直接输出简洁自然语言结果，不要输出 JSON。"
            "不得声称未由 Tool 结果证实的业务写入已成功。"
            "若存在 WAITING_APPROVAL 的 Tool，必须说明该动作已提交人工确认、等待审批后生效。"
        )
        user_prompt = (
            f"Skill: {skill}\n阶段能力说明: {guidance or '（未声明）'}\n"
            f"用户指令: {message}\n"
            f"可回溯资源引用数: {resource_count}\n"
            f"结构化 Tool 观察: {json.dumps(tool_calls, ensure_ascii=False)}"
        )
        try:
            with self._client.stream(
                "POST",
                self._endpoint,
                json={
                    "model": self._model,
                    "messages": [
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": user_prompt},
                    ],
                    "temperature": 0,
                    "stream": True,
                },
            ) as response:
                response.raise_for_status()
                for line in response.iter_lines():
                    if not line.startswith("data: "):
                        continue
                    data = line[6:]
                    if data == "[DONE]":
                        return
                    payload = json.loads(data)
                    choices = payload.get("choices")
                    # 百炼可能在 [DONE] 前发送只有 usage、choices 为空的尾帧。
                    # 该帧不是协议错误，也不包含可展示正文，应直接跳过。
                    if not isinstance(choices, list) or not choices:
                        continue
                    first_choice = choices[0]
                    if not isinstance(first_choice, dict):
                        raise TypeError("stream choice must be an object")
                    choice_delta = first_choice.get("delta")
                    if not isinstance(choice_delta, dict):
                        continue
                    # 思考内容单独交给 sink，绝不作为正文增量产出。
                    self._emit_thinking(choice_delta.get("reasoning_content"))
                    delta = choice_delta.get("content")
                    if delta:
                        yield str(delta)
        except httpx.TimeoutException as exception:
            raise ModelGatewayError("AGENT_MODEL_TIMEOUT", retryable=True) from exception
        except httpx.HTTPError as exception:
            raise ModelGatewayError("AGENT_MODEL_UNAVAILABLE", retryable=True) from exception
        except (json.JSONDecodeError, KeyError, TypeError, ValueError) as exception:
            raise ModelGatewayError("AGENT_MODEL_INVALID_RESPONSE", retryable=False) from exception

    def _complete_json(self, system_prompt: str, user_prompt: str) -> dict[str, Any]:
        for attempt in range(1, self._max_attempts + 1):
            try:
                response = self._client.post(
                    self._endpoint,
                    json={
                        "model": self._model,
                        "messages": [
                            {"role": "system", "content": system_prompt},
                            {"role": "user", "content": user_prompt},
                        ],
                        "response_format": {"type": "json_object"},
                        "temperature": 0,
                    },
                )
                response.raise_for_status()
                message = response.json()["choices"][0]["message"]
                if isinstance(message, dict):
                    self._emit_thinking(message.get("reasoning_content"))
                content = message["content"]
                parsed = json.loads(content)
                if not isinstance(parsed, dict):
                    raise ValueError("root must be an object")
                return parsed
            except httpx.TimeoutException as exception:
                if attempt < self._max_attempts:
                    self._backoff(attempt)
                    continue
                raise ModelGatewayError("AGENT_MODEL_TIMEOUT", retryable=True) from exception
            except httpx.HTTPStatusError as exception:
                status_code = exception.response.status_code
                retryable = status_code == 429 or status_code >= 500
                if retryable and attempt < self._max_attempts:
                    self._backoff(attempt)
                    continue
                raise ModelGatewayError(
                    "AGENT_MODEL_UNAVAILABLE" if retryable else "AGENT_MODEL_REQUEST_REJECTED",
                    retryable=retryable,
                ) from exception
            except httpx.RequestError as exception:
                if attempt < self._max_attempts:
                    self._backoff(attempt)
                    continue
                raise ModelGatewayError("AGENT_MODEL_UNAVAILABLE", retryable=True) from exception
            except (
                json.JSONDecodeError,
                KeyError,
                TypeError,
                ValueError,
                ValidationError,
            ) as exception:
                raise ModelGatewayError(
                    "AGENT_MODEL_INVALID_RESPONSE", retryable=False
                ) from exception
        raise AssertionError("model attempt loop must return or raise")

    def _backoff(self, attempt: int) -> None:
        """仅在 Provider 可恢复失败时执行有界指数退避。"""

        if self._retry_backoff_seconds > 0:
            time.sleep(self._retry_backoff_seconds * (2 ** (attempt - 1)))
