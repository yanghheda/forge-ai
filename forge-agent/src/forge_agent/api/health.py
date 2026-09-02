"""容器存活与内部就绪端点。"""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from forge_agent.api.auth import require_internal_credential

router = APIRouter()


class HealthResponse(BaseModel):
    """健康端点的稳定响应。"""

    application: str
    status: str


@router.get("/healthz", response_model=HealthResponse, include_in_schema=False)
def liveness() -> HealthResponse:
    """仅证明 HTTP 进程存活，不表达内部调用授权。"""

    return HealthResponse(application="forge-agent", status="UP")


@router.get(
    "/internal/v1/health/ready",
    response_model=HealthResponse,
    include_in_schema=False,
)
def readiness(
    _credential: Annotated[None, Depends(require_internal_credential)],
) -> HealthResponse:
    """供 forge-server 使用服务 JWT 探测配置就绪状态。"""

    return HealthResponse(application="forge-agent", status="READY")
