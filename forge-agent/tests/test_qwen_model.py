from __future__ import annotations

import json

import httpx
import pytest

from forge_agent.gateway.qwen import QwenLanguageModel


def qwen_model(responses: list[dict[str, object]]) -> tuple[QwenLanguageModel, list[dict]]:
    requests: list[dict] = []

    def handle(request: httpx.Request) -> httpx.Response:
        requests.append(json.loads(request.content))
        response = responses.pop(0)
        return httpx.Response(200, json=response)

    client = httpx.Client(transport=httpx.MockTransport(handle))
    return (
        QwenLanguageModel(
            api_key="sk-test",
            base_url="https://dashscope.example/compatible-mode/v1",
            model="qwen-plus",
            client=client,
        ),
        requests,
    )


def completion(content: str) -> dict[str, object]:
    return {"choices": [{"message": {"content": content}}]}


def test_qwen_creates_structured_plan() -> None:
    model, requests = qwen_model([completion('{"steps":["理解目标","形成结果"]}')])

    plan = model.create_plan("UX", "整理交付方案")

    assert plan == ["理解目标", "形成结果"]
    assert requests[0]["model"] == "qwen-plus"
    assert requests[0]["response_format"] == {"type": "json_object"}


def test_qwen_selects_tool_or_finishes() -> None:
    model, _ = qwen_model(
        [
            completion('{"toolName":"get_work_item","arguments":{"workItemId":42}}'),
            completion('{"toolName":null,"arguments":{}}'),
        ]
    )

    tools = [
        {
            "name": "get_work_item",
            "description": "读取工作项",
            "inputSchema": {"type": "object"},
        }
    ]
    selection = model.select_tool("PRODUCT", "读取需求", tools, [])
    finished = model.select_tool("PRODUCT", "读取需求", tools, ["get_work_item"])

    assert selection is not None
    assert selection.tool_name == "get_work_item"
    assert selection.arguments == {"workItemId": 42}
    assert finished is None


def test_qwen_prompt_contains_only_manifest_tools_and_structured_observations() -> None:
    model, requests = qwen_model(
        [
            completion('{"toolName":null,"arguments":{}}'),
            completion('{"answer":"需求读取完成"}'),
        ]
    )

    model.select_tool(
        "PRODUCT",
        "读取需求",
        [
            {
                "name": "get_work_item",
                "description": "读取工作项",
                "inputSchema": {"type": "object"},
            }
        ],
        [],
    )
    answer = model.finalize(
        "PRODUCT",
        "读取需求",
        1,
        [{"toolName": "get_work_item", "status": "SUCCEEDED", "result": {"id": 42}}],
    )

    assert "get_work_item" in requests[0]["messages"][1]["content"]
    assert "inputSchema" in requests[0]["messages"][1]["content"]
    assert "读取需求" in requests[1]["messages"][1]["content"]
    assert '"id": 42' in requests[1]["messages"][1]["content"]
    assert answer == "需求读取完成"


def test_qwen_rejects_invalid_structured_output() -> None:
    model, _ = qwen_model([completion("not-json")])

    with pytest.raises(RuntimeError, match="invalid JSON"):
        model.create_plan("UX", "整理方案")


def test_qwen_propagates_http_failure_without_leaking_key() -> None:
    def handle(request: httpx.Request) -> httpx.Response:
        return httpx.Response(401, json={"message": "invalid api key"})

    model = QwenLanguageModel(
        api_key="sk-sensitive",
        base_url="https://dashscope.example/compatible-mode/v1",
        model="qwen-plus",
        client=httpx.Client(transport=httpx.MockTransport(handle)),
    )

    with pytest.raises(RuntimeError) as raised:
        model.finalize("UX", "整理方案", 1, [])

    assert "sk-sensitive" not in str(raised.value)
