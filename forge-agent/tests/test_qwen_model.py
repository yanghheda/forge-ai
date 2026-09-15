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
    selection = model.select_tool("PRODUCT", "读取需求", 42, tools, [])
    finished = model.select_tool("PRODUCT", "读取需求", 42, tools, [{"toolName": "get_work_item"}])

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
        42,
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
    assert "Requirement ID: 42" in requests[0]["messages"][1]["content"]
    assert "读取需求" in requests[1]["messages"][1]["content"]
    assert '"id": 42' in requests[1]["messages"][1]["content"]
    assert answer == "需求读取完成"


def test_qwen_prompt_for_create_prd_document_requires_full_body() -> None:
    """PRD 必须由模型一次性生成完整正文，而不是只报计划/章节名/占位文本。"""

    model, requests = qwen_model([completion('{"toolName":"create_prd_document","arguments":{}}')])

    model.select_tool(
        "PRODUCT",
        "新建PRD",
        7,
        [
            {
                "name": "create_prd_document",
                "description": "在 Requirement 下创建 PRD",
                "inputSchema": {"type": "object"},
            }
        ],
        [],
    )

    content = requests[0]["messages"][0]["content"]
    assert "contentMarkdown" in content
    assert "完整" in content
    assert "PRD 正文" in content
    assert "占位文本" in content


def test_qwen_prompt_requires_generated_requirement_description() -> None:
    model, requests = qwen_model(
        [
            completion(
                '{"toolName":"create_requirement","arguments":'
                '{"title":"库存预警","description":"为运营提供库存阈值预警，减少缺货。"}}'
            )
        ]
    )

    model.select_tool(
        "PRODUCT",
        "创建一个库存预警需求",
        None,
        [
            {
                "name": "create_requirement",
                "description": "创建需求",
                "inputSchema": {"type": "object"},
            }
        ],
        [],
    )

    content = requests[0]["messages"][0]["content"]
    assert "create_requirement" in content
    assert "description 必填" in content
    assert "不得使用空字符串" in content


def test_qwen_prompt_requires_prd_creation_after_successful_search() -> None:
    model, requests = qwen_model([completion('{"toolName":"create_prd_document","arguments":{}}')])

    model.select_tool(
        "PRODUCT",
        "检索背景并填充 PRD 正文",
        7,
        [
            {
                "name": "search_documents",
                "description": "检索文档",
                "inputSchema": {"type": "object"},
            },
            {
                "name": "create_prd_document",
                "description": "创建 PRD",
                "inputSchema": {"type": "object"},
            },
        ],
        [
            {
                "toolName": "search_documents",
                "status": "SUCCEEDED",
                "result": {"items": []},
            }
        ],
    )

    content = requests[0]["messages"][0]["content"]
    assert "不得再次调用 search_documents" in content
    assert "必须选择 create_prd_document" in content


def test_qwen_prompt_requires_transition_after_successful_work_item_read() -> None:
    model, requests = qwen_model([completion('{"toolName":"advance_requirement","arguments":{}}')])

    model.select_tool(
        "PRODUCT",
        "推进当前阶段",
        7,
        [
            {
                "name": "get_work_item",
                "description": "读取需求",
                "inputSchema": {"type": "object"},
            },
            {
                "name": "advance_requirement",
                "description": "推进需求阶段",
                "inputSchema": {"type": "object"},
            },
        ],
        [
            {
                "toolName": "get_work_item",
                "status": "SUCCEEDED",
                "result": {"id": 7, "status": "DRAFT", "version": 3},
            }
        ],
    )

    content = requests[0]["messages"][0]["content"]
    assert "不得再次调用 get_work_item" in content
    assert "必须选择 advance_requirement" in content


def test_qwen_rejects_invalid_structured_output() -> None:
    model, _ = qwen_model([completion("not-json")])

    with pytest.raises(RuntimeError, match="invalid JSON"):
        model.create_plan("UX", "整理方案")


def test_qwen_streams_final_answer_as_plain_text_deltas() -> None:
    requests: list[dict] = []

    def handle(request: httpx.Request) -> httpx.Response:
        requests.append(json.loads(request.content))
        body = (
            'data: {"choices":[{"delta":{"content":"需求"}}]}\n\n'
            'data: {"choices":[{"delta":{"content":"已完成"}}]}\n\n'
            'data: {"choices":[],"usage":{"total_tokens":12}}\n\n'
            "data: [DONE]\n\n"
        )
        return httpx.Response(200, content=body.encode())

    model = QwenLanguageModel(
        api_key="sk-test",
        base_url="https://dashscope.example/compatible-mode/v1",
        model="qwen-plus",
        client=httpx.Client(transport=httpx.MockTransport(handle)),
    )

    deltas = list(model.stream_final("PRODUCT", "完成需求", 1, []))

    assert deltas == ["需求", "已完成"]
    assert requests[0]["stream"] is True
    assert "response_format" not in requests[0]


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
