from __future__ import annotations

from datetime import UTC, datetime, timedelta

import jwt
from fastapi.testclient import TestClient

from forge_agent.main import create_app
from forge_agent.settings import AgentSettings

SECRET = "test-only-internal-jwt-secret-32-bytes-minimum"


def create_token(
    *,
    secret: str = SECRET,
    audience: str = "forge-agent",
    expires_delta: timedelta = timedelta(minutes=1),
) -> str:
    now = datetime.now(UTC)
    return jwt.encode(
        {
            "iss": "forge-server",
            "sub": "forge-server",
            "aud": audience,
            "iat": now,
            "exp": now + expires_delta,
        },
        secret,
        algorithm="HS256",
    )


def client() -> TestClient:
    settings = AgentSettings(internal_jwt_secret=SECRET, _env_file=None)
    return TestClient(create_app(settings))


def test_liveness_is_available_without_internal_credential() -> None:
    response = client().get("/healthz")

    assert response.status_code == 200
    assert response.json() == {"application": "forge-agent", "status": "UP"}


def test_internal_readiness_accepts_valid_server_token() -> None:
    response = client().get(
        "/internal/v1/health/ready",
        headers={"Authorization": f"Bearer {create_token()}"},
    )

    assert response.status_code == 200
    assert response.json() == {"application": "forge-agent", "status": "READY"}


def test_internal_readiness_rejects_missing_token() -> None:
    response = client().get("/internal/v1/health/ready")

    assert response.status_code == 401
    assert response.json()["detail"] == "invalid internal credential"


def test_internal_readiness_rejects_wrong_audience() -> None:
    response = client().get(
        "/internal/v1/health/ready",
        headers={"Authorization": f"Bearer {create_token(audience='other-service')}"},
    )

    assert response.status_code == 401


def test_internal_readiness_rejects_expired_token() -> None:
    response = client().get(
        "/internal/v1/health/ready",
        headers={"Authorization": f"Bearer {create_token(expires_delta=timedelta(seconds=-1))}"},
    )

    assert response.status_code == 401
