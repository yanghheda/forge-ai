"""FastAPI 应用入口。"""

from __future__ import annotations

from fastapi import FastAPI

from forge_agent.api.health import router as health_router
from forge_agent.api.runs import router as runs_router
from forge_agent.contracts.registry import ToolContractRegistry
from forge_agent.gateway.runtime import FakeLanguageModel, LangGraphRuntimeGateway, RuntimeGateway
from forge_agent.gateway.tool_transport import ServerToolTransport
from forge_agent.settings import AgentSettings


def create_app(
    settings: AgentSettings | None = None,
    runtime: RuntimeGateway | None = None,
) -> FastAPI:
    """创建应用并在注册路由前完成配置与契约校验。"""

    resolved_settings = settings or AgentSettings()
    registry = ToolContractRegistry(resolved_settings.contracts_root)
    app = FastAPI(title="ForgeAI Agent", docs_url=None, redoc_url=None, openapi_url=None)
    app.state.settings = resolved_settings
    app.state.contracts = registry
    app.state.runtime = runtime or LangGraphRuntimeGateway(
        resolved_settings.checkpoint_path,
        FakeLanguageModel(),
        ServerToolTransport(resolved_settings.server_base_url),
    )
    app.include_router(health_router)
    app.include_router(runs_router)
    return app
