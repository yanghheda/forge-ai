"""Backend 调用的内部 Agent Run 端点。"""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status

from forge_agent.api.auth import require_run_credential
from forge_agent.gateway.runtime import RunCancelled, RunResult, RunStart, RuntimeGateway

router = APIRouter(prefix="/internal/v1/runs", include_in_schema=False)


def get_runtime(request: Request) -> RuntimeGateway:
    """读取应用启动时构造的单例 Runtime。"""

    return request.app.state.runtime


@router.post("/{run_id}/start", response_model=RunResult, response_model_by_alias=False)
def start_run(
    run_id: str,
    body: RunStart,
    run_token: Annotated[str, Depends(require_run_credential)],
    runtime: Annotated[RuntimeGateway, Depends(get_runtime)],
) -> RunResult:
    """校验路径与 Manifest 一致后按 Run ID 幂等执行最小图。"""

    if run_id != body.manifest.run_id:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="run id mismatch")
    try:
        return runtime.start(body, run_token)
    except ValueError as exception:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exception)
        ) from exception
    except RunCancelled as exception:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT, detail="run cancelled"
        ) from exception


@router.post(
    "/{run_id}/cancel",
    status_code=status.HTTP_204_NO_CONTENT,
    response_class=Response,
)
def cancel_run(
    run_id: str,
    run_token: Annotated[str, Depends(require_run_credential)],
    runtime: Annotated[RuntimeGateway, Depends(get_runtime)],
) -> Response:
    """设置进程内取消信号，使正在生成的 Run 尽快退出。"""

    del run_token
    runtime.cancel(run_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
