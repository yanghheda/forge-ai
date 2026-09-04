from __future__ import annotations

import os
from pathlib import Path

# 测试统一使用仓库真实契约目录；等价于部署时挂载的契约卷。
REPO_CONTRACTS = Path(__file__).resolve().parents[2] / "packages" / "forge-contracts"
os.environ.setdefault("FORGE_AGENT_CONTRACTS_ROOT", str(REPO_CONTRACTS))
