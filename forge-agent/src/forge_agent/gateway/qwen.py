"""阿里云百炼千问模型的受控 OpenAI 兼容适配器。"""

from __future__ import annotations

import json
from typing import Any

import httpx
from pydantic import BaseModel, Field, ValidationError

from forge_agent.gateway.runtime import ToolSelection


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
        client: httpx.Client | None = None,
    ) -> None:
        self._model = model
        self._endpoint = f"{base_url.rstrip('/')}/chat/completions"
        self._client = client or httpx.Client(
            timeout=timeout_seconds,
            headers={"Authorization": f"Bearer {api_key}"},
        )

    def create_plan(self, skill: str, message: str) -> list[str]:
        payload = self._complete_json(
            "你是 ForgeAI 的计划节点。只返回 JSON："
            '{"steps":["步骤"]}。步骤必须简短、可执行；计划不代表授权。',
            f"Skill: {skill}\n用户指令: {message}",
        )
        return _PlanResponse.model_validate(payload).steps

    def select_tool(
        self,
        skill: str,
        message: str,
        work_item_id: int | None,
        tool_definitions: list[dict[str, Any]],
        tool_calls: list[dict[str, Any]],
    ) -> ToolSelection | None:
        payload = self._complete_json(
            "你是 ForgeAI 的 Tool 选择节点。只返回 JSON："
            '{"toolName":"工具名或 null","arguments":{}}。'
            "不要把自然语言结果伪装成 Tool 执行结果；不得猜测资源 ID 或 expectedVersion，"
            "缺少时先调用读取 Tool；必须依据结构化观察决定下一步；没有必要调用时返回 null。",
            (
                f"Skill: {skill}\n用户指令: {message}\n"
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
    ) -> str:
        payload = self._complete_json(
            "你是 ForgeAI 的结果收尾节点。只返回 JSON："
            '{"answer":"简洁结果"}。不得声称未由 Tool 结果证实的业务写入已成功。',
            (
                f"Skill: {skill}\n用户指令: {message}\n"
                f"可回溯资源引用数: {resource_count}\n"
                f"结构化 Tool 观察: {json.dumps(tool_calls, ensure_ascii=False)}"
            ),
        )
        return _AnswerResponse.model_validate(payload).answer

    def _complete_json(self, system_prompt: str, user_prompt: str) -> dict[str, Any]:
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
            content = response.json()["choices"][0]["message"]["content"]
            parsed = json.loads(content)
            if not isinstance(parsed, dict):
                raise ValueError("root must be an object")
            return parsed
        except json.JSONDecodeError as exception:
            raise RuntimeError("Qwen returned invalid JSON") from exception
        except (httpx.HTTPError, KeyError, TypeError, ValueError, ValidationError) as exception:
            raise RuntimeError("Qwen request or response validation failed") from exception
