from __future__ import annotations

import json

import httpx
import pytest

from forge_agent.gateway.tool_transport import (
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


def test_execute_maps_non_ok_status_to_failed_envelope() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(409, json={"code": "VALIDATION_FAILED", "message": "conflict"})

    execution = transport_with(handler).execute("get_project", "call-3", {}, "token")

    assert execution.status == "FAILED"
    assert execution.error_code == "SERVER_ERROR"
    assert "409" in (execution.error_message or "")


def test_execute_raises_on_unauthorized_credential() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(401)

    with pytest.raises(ToolTransportFailure, match="credential rejected"):
        transport_with(handler).execute("get_project", "call-4", {}, "expired-token")


def test_execute_raises_on_network_failure() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection refused")

    with pytest.raises(ToolTransportFailure, match="request failed"):
        transport_with(handler).execute("get_project", "call-5", {}, "token")


def test_execute_raises_on_invalid_payload() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200, content=b"not-json", headers={"Content-Type": "application/json"}
        )

    with pytest.raises(ToolTransportFailure, match="not valid"):
        transport_with(handler).execute("get_project", "call-6", {}, "token")
