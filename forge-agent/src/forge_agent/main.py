"""FastAPI 应用入口。"""

from __future__ import annotations

from fastapi import FastAPI

from forge_agent.api.health import router as health_router
from forge_agent.api.runs import router as runs_router
from forge_agent.contracts.registry import ToolContractRegistry
from forge_agent.gateway.qwen import QwenLanguageModel
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
    if runtime is None:
        if resolved_settings.model_provider == "qwen":
            api_key = resolved_settings.qwen_api_key
            assert api_key is not None
            model = QwenLanguageModel(
                api_key=api_key.get_secret_value(),
                base_url=resolved_settings.qwen_base_url,
                model=resolved_settings.qwen_model,
                timeout_seconds=resolved_settings.qwen_timeout_seconds,
            )
        else:
            model = FakeLanguageModel()
        runtime = LangGraphRuntimeGateway(
            resolved_settings.checkpoint_path,
            model,
            ServerToolTransport(resolved_settings.server_base_url),
        )
    app.state.runtime = runtime
    app.include_router(health_router)
    app.include_router(runs_router)
    return app
