"""Agent 进程配置及启动时安全校验。"""

from __future__ import annotations

from pathlib import Path

from pydantic import Field
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
    contracts_root: Path = Path("packages/forge-contracts")
