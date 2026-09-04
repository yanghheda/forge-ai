# forge-agent

ForgeAI 的 FastAPI Agent Runtime 骨架，负责承载后续上下文构建、检索、计划、Tool 选择和结果解释。它不得直接访问业务 MySQL，也不得持有 GitLab Token 或部署凭据。

## 本地检查

目标运行时为 Python 3.12。安装锁定依赖后运行：

```bash
python3.12 -m venv forge-agent/.venv
forge-agent/.venv/bin/pip install -r forge-agent/requirements.lock
FORGE_AGENT_PYTHON=forge-agent/.venv/bin/python make agent-test
FORGE_AGENT_PYTHON=forge-agent/.venv/bin/python make contracts-check
```

`/healthz` 只用于容器存活检查；`/internal/v1/health/ready` 必须携带由 `forge-server` 签发的短时服务 JWT。当前 `RuntimeGateway` 只是框架隔离边界，不包含 Deep Agents、Agent Run 或业务 Tool。
