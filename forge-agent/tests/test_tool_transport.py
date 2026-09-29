from __future__ import annotations

import json

import httpx
import pytest

from forge_agent.gateway.tool_transport import (
    ServerAnswerStream,
    ServerToolTransport,
    ToolTransportFailure,
)


def transport_with(handler) -> ServerToolTransport:
    client = httpx.Client(
        transport=httpx.MockTransport(handler), base_url="http://forge-server:8080"
    )
    transport = ServerToolTransport.__new__(ServerToolTransport)
    transport._client = client
    return transport


def test_internal_transport_does_not_inherit_environment_proxy(monkeypatch) -> None:
    captured: dict[str, object] = {}

    class ClientSpy:
        def __init__(self, **kwargs) -> None:
            captured.update(kwargs)

    monkeypatch.setattr(httpx, "Client", ClientSpy)

    ServerToolTransport("http://127.0.0.1:8080")

    assert captured["base_url"] == "http://127.0.0.1:8080"
    assert captured["trust_env"] is False


def test_execute_posts_credential_and_parses_success_envelope() -> None:
    seen: dict[str, str] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["authorization"] = request.headers.get("Authorization", "")
        seen["body"] = json.loads(request.content.decode("utf-8"))
        return httpx.Response(
            200,
            json={
                "status": "SUCCEEDED",
                "toolName": "create_requirement",
                "toolCallId": "call-1",
                "replayed": False,
                "result": {"id": 42, "itemKey": "FORGE-42"},
                "errorCode": None,
                "errorMessage": None,
            },
        )

    execution = transport_with(handler).execute(
        "create_requirement", "call-1", {"title": "首个需求"}, "run-token"
    )

    assert seen["url"] == "http://forge-server:8080/internal/v1/tools/create_requirement:execute"
    assert seen["authorization"] == "Bearer run-token"
    assert seen["body"] == {"toolCallId": "call-1", "arguments": {"title": "首个需求"}}
    assert execution.status == "SUCCEEDED"
    assert execution.replayed is False
    assert execution.result == {"id": 42, "itemKey": "FORGE-42"}


def test_execute_normalizes_pending_confirmation_and_rejected() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "status": "PENDING_CONFIRMATION",
                "toolName": "create_requirement",
                "toolCallId": "call-2",
                "replayed": False,
                "result": None,
                "errorCode": None,
                "errorMessage": None,
            },
        )

    execution = transport_with(handler).execute("create_requirement", "call-2", {}, "token")

    assert execution.status == "PENDING_CONFIRMATION"
    assert execution.result is None


def test_execute_preserves_stable_server_error_for_agent_explanation() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(409, json={"code": "VALIDATION_FAILED", "message": "conflict"})

    execution = transport_with(handler).execute("get_organization", "call-3", {}, "token")

    assert execution.status == "FAILED"
    assert execution.error_code == "VALIDATION_FAILED"
    assert execution.error_message == "conflict"


def test_execute_raises_on_unauthorized_credential() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(401)

    with pytest.raises(ToolTransportFailure, match="credential rejected"):
        transport_with(handler).execute("get_organization", "call-4", {}, "expired-token")


def test_execute_raises_on_network_failure() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection refused")

    with pytest.raises(ToolTransportFailure, match="request failed"):
        transport_with(handler).execute("get_organization", "call-5", {}, "token")


def test_execute_raises_on_invalid_payload() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200, content=b"not-json", headers={"Content-Type": "application/json"}
        )

    with pytest.raises(ToolTransportFailure, match="not valid"):
        transport_with(handler).execute("get_organization", "call-6", {}, "token")


def answer_stream_with(handler) -> ServerAnswerStream:
    client = httpx.Client(
        transport=httpx.MockTransport(handler), base_url="http://forge-server:8080"
    )
    stream = ServerAnswerStream.__new__(ServerAnswerStream)
    stream._client = client
    return stream


def test_publish_thinking_posts_node_and_delta_with_run_credential() -> None:
    seen: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["authorization"] = request.headers.get("Authorization", "")
        seen["body"] = json.loads(request.content.decode("utf-8"))
        return httpx.Response(204)

    answer_stream_with(handler).publish_thinking(
        "01JRUN0000000000000000000", "create_plan", "先拆解用户目标", "run-token"
    )

    assert seen["url"] == (
        "http://forge-server:8080/internal/v1/agent-runs/01JRUN0000000000000000000/thinking-deltas"
    )
    assert seen["authorization"] == "Bearer run-token"
    assert seen["body"] == {"delta": "先拆解用户目标", "node": "create_plan"}


def test_publish_reasoning_omits_node_field() -> None:
    seen: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["body"] = json.loads(request.content.decode("utf-8"))
        return httpx.Response(204)

    answer_stream_with(handler).publish_reasoning("01JRUN", "计划项", "run-token")

    assert seen["url"] == "http://forge-server:8080/internal/v1/agent-runs/01JRUN/reasoning-deltas"
    assert seen["body"] == {"delta": "计划项"}


def test_publish_tool_posts_event_and_omits_none_optional_fields() -> None:
    seen: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["authorization"] = request.headers.get("Authorization", "")
        seen["body"] = json.loads(request.content.decode("utf-8"))
        return httpx.Response(204)

    answer_stream_with(handler).publish_tool(
        "01JRUN0000000000000000000",
        call_id="call-1",
        tool_name="create_requirement",
        event="tool.started",
        status="RUNNING",
        summary=None,
        duration_ms=None,
        run_token="run-token",
    )

    assert seen["url"] == (
        "http://forge-server:8080/internal/v1/agent-runs/01JRUN0000000000000000000/tool-events"
    )
    assert seen["authorization"] == "Bearer run-token"
    assert seen["body"] == {
        "callId": "call-1",
        "toolName": "create_requirement",
        "event": "tool.started",
        "status": "RUNNING",
    }


def test_publish_tool_includes_optional_fields_when_set() -> None:
    seen: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["body"] = json.loads(request.content.decode("utf-8"))
        return httpx.Response(204)

    answer_stream_with(handler).publish_tool(
        "01JRUN",
        call_id="call-2",
        tool_name="advance_requirement",
        event="tool.failed",
        status="FAILED",
        summary="WORKFLOW_GUARD_FAILED",
        duration_ms=12,
        run_token="run-token",
    )

    assert seen["body"] == {
        "callId": "call-2",
        "toolName": "advance_requirement",
        "event": "tool.failed",
        "status": "FAILED",
        "summary": "WORKFLOW_GUARD_FAILED",
        "durationMs": 12,
    }


def test_publish_tool_raises_transport_failure_on_error_status() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(500)

    with pytest.raises(ToolTransportFailure, match="answer stream publish failed"):
        answer_stream_with(handler).publish_tool(
            "01JRUN",
            call_id="call-3",
            tool_name="get_work_item",
            event="tool.completed",
            status="SUCCEEDED",
            summary=None,
            duration_ms=3,
            run_token="run-token",
        )
