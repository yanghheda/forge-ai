from __future__ import annotations

from datetime import UTC, datetime, timedelta

import jwt
from fastapi.testclient import TestClient

from forge_agent.gateway.runtime import RunResult
from forge_agent.gateway.tool_transport import ToolCallRecord
from forge_agent.main import create_app
from forge_agent.settings import AgentSettings

SECRET = "test-only-internal-jwt-secret-32-bytes-minimum"
RUN_ID = "01JTEST0000000000000000000"


def token(
    *,
    expires_delta: timedelta = timedelta(minutes=1),
    secret: str = SECRET,
    run_id: str = RUN_ID,
) -> str:
    now = datetime.now(UTC)
    return jwt.encode(
        {
            "iss": "forge-server",
            "sub": "forge-server",
            "aud": "forge-agent",
            "iat": now,
            "exp": now + expires_delta,
            "run_id": run_id,
        },
        secret,
        algorithm="HS256",
    )


def body() -> dict[str, object]:
    return {
        "message": "生成计划",
        "manifest": {
            "runId": RUN_ID,
            "subject": {"userId": 12},
            "scope": {"organizationId": 1},
            "skill": "PRODUCT",
            "effectiveToolNames": [],
            "policy": {"mediumConfirmation": "ASK", "maxToolCalls": 20, "tokenBudget": 50000},
            "resourceRefs": [],
            "expiresAt": (datetime.now(UTC) + timedelta(minutes=5)).isoformat(),
        },
    }


def client(tmp_path, runtime=None) -> TestClient:
    settings = AgentSettings(
        internal_jwt_secret=SECRET,
        checkpoint_path=tmp_path / "checkpoints.sqlite",
        _env_file=None,
    )
    return TestClient(create_app(settings, runtime=runtime))


class ToolResultRuntime:
    def start(self, request, run_token) -> RunResult:
        return RunResult(
            status="SUCCEEDED",
            plan=["创建需求"],
            answer="参数校验失败",
            tool_calls=[
                ToolCallRecord(
                    toolName="create_requirement",
                    toolCallId="call-1",
                    status="FAILED",
                    errorCode="VALIDATION_FAILED",
                )
            ],
            state_version=8,
        )


def test_start_requires_valid_internal_credential(tmp_path) -> None:
    app = client(tmp_path)

    assert app.post(f"/internal/v1/runs/{RUN_ID}/start", json=body()).status_code == 401
    assert (
        app.post(
            f"/internal/v1/runs/{RUN_ID}/start",
            json=body(),
            headers={"Authorization": f"Bearer {token(secret='x' * 32)}"},
        ).status_code
        == 401
    )
    assert (
        app.post(
            f"/internal/v1/runs/{RUN_ID}/start",
            json=body(),
            headers={"Authorization": f"Bearer {token(expires_delta=timedelta(seconds=-1))}"},
        ).status_code
        == 401
    )


def test_start_is_idempotent_by_path_run_id(tmp_path) -> None:
    app = client(tmp_path)
    headers = {"Authorization": f"Bearer {token()}"}

    first = app.post(f"/internal/v1/runs/{RUN_ID}/start", json=body(), headers=headers)
    repeated = app.post(f"/internal/v1/runs/{RUN_ID}/start", json=body(), headers=headers)

    assert first.status_code == 200
    assert repeated.status_code == 200
    assert repeated.json() == first.json()
    assert first.json()["status"] == "SUCCEEDED"
    # server 端 StartResponse 以 snake_case 解析 state_version；防止 alias 回归。
    assert "state_version" in first.json()
    assert first.json()["tool_calls"] == []


def test_start_serializes_nested_tool_trace_with_snake_case_contract(tmp_path) -> None:
    app = client(tmp_path, ToolResultRuntime())

    response = app.post(
        f"/internal/v1/runs/{RUN_ID}/start",
        json=body(),
        headers={"Authorization": f"Bearer {token()}"},
    )

    assert response.status_code == 200
    assert response.json()["tool_calls"] == [
        {
            "tool_name": "create_requirement",
            "tool_call_id": "call-1",
            "status": "FAILED",
            "replayed": False,
            "error_code": "VALIDATION_FAILED",
            "error_message": None,
            "result": None,
            "approval_id": None,
        }
    ]


def test_start_rejects_manifest_for_another_run(tmp_path) -> None:
    app = client(tmp_path)

    response = app.post(
        "/internal/v1/runs/01JOTHER000000000000000000/start",
        json=body(),
        headers={"Authorization": f"Bearer {token(run_id='01JOTHER000000000000000000')}"},
    )

    assert response.status_code == 409
