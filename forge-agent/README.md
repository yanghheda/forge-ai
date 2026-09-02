# forge-agent

ForgeAI 的 FastAPI Agent Runtime，负责上下文构建、检索、计划、Tool 选择和结果解释。它不得直接访问业务 MySQL，也不得持有 GitLab Token 或部署凭据。

应用骨架将在会话 05 建立；本轮不创建 Runtime 或 Tool Contract。

