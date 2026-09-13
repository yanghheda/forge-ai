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

`/healthz` 只用于容器存活检查；`/internal/v1/health/ready` 和 Run 启动端点必须携带由 `forge-server` 签发的短时服务 JWT。`RuntimeGateway` 使用 LangGraph 的有界最小图与 SQLite Checkpoint，Fake LLM 只验证计划、完成和恢复链路；本轮不包含业务 Tool。

## 接入阿里云百炼千问

运行时通过百炼 OpenAI 兼容接口调用千问。API Key 只注入 `forge-agent`，不得传给
`forge-server`、浏览器或写入仓库：

```bash
export FORGE_AGENT_MODEL_PROVIDER=qwen
export FORGE_AGENT_QWEN_API_KEY='<百炼 API Key>'
export FORGE_AGENT_QWEN_MODEL=qwen-plus
```

默认地址是北京地域的
`https://dashscope.aliyuncs.com/compatible-mode/v1`。使用其他地域或业务空间时，通过
`FORGE_AGENT_QWEN_BASE_URL` 配置对应的 OpenAI 兼容地址。未显式启用 `qwen` 时仍使用
确定性的 Fake 模型，保证本地测试不会产生外部调用和费用。
