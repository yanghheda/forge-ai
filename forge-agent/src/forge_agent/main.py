"""FastAPI 应用入口。"""

from __future__ import annotations

from fastapi import FastAPI

from forge_agent.api.health import router as health_router
from forge_agent.settings import AgentSettings


def create_app(settings: AgentSettings | None = None) -> FastAPI:
    """创建应用并在注册路由前完成配置校验。"""

    resolved_settings = settings or AgentSettings()
    app = FastAPI(title="ForgeAI Agent", docs_url=None, redoc_url=None, openapi_url=None)
    app.state.settings = resolved_settings
    app.include_router(health_router)
    return app
