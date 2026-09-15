"""Agent 进程配置及启动时安全校验。"""

from __future__ import annotations

from pathlib import Path
from typing import Literal

from pydantic import Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class AgentSettings(BaseSettings):
    """只接收 Agent Runtime 本轮所需的最小配置。"""

    model_config = SettingsConfigDict(
        env_prefix="FORGE_AGENT_",
        env_file=".env",
        extra="ignore",
    )

    internal_jwt_secret: str = Field(min_length=32, repr=False)
    internal_jwt_issuer: str = "forge-server"
    internal_jwt_audience: str = "forge-agent"
    checkpoint_path: Path = Path("data/checkpoints.sqlite")
    server_base_url: str = Field(default="http://forge-server:8080", min_length=1)
    publish_answer_deltas: bool = True
    contracts_root: Path = Path("packages/forge-contracts")
    model_provider: Literal["fake", "qwen"] = "fake"
    qwen_api_key: SecretStr | None = Field(default=None, repr=False)
    qwen_base_url: str = Field(
        default="https://dashscope.aliyuncs.com/compatible-mode/v1",
        min_length=1,
    )
    qwen_model: str = Field(default="qwen-plus", min_length=1)
    qwen_timeout_seconds: float = Field(default=30.0, gt=0, le=120)

    @model_validator(mode="after")
    def require_provider_credentials(self) -> AgentSettings:
        """启用真实模型时强制提供密钥，避免启动后才静默回退到 Fake。"""

        if self.model_provider == "qwen" and (
            self.qwen_api_key is None or not self.qwen_api_key.get_secret_value()
        ):
            raise ValueError("qwen_api_key is required when model_provider is qwen")
        return self
