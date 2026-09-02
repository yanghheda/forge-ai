# ForgeAI

> AI-Native Software Delivery Workbench

ForgeAI is an open-source, self-hosted AI-native software delivery workbench. It connects Product, UX, Development, QA and Release workflows with AI Agents, enabling teams to manage requirements, documents, code collaboration, CI/CD and delivery automation in one workspace.

ForgeAI 采用 Monorepo 管理三个独立应用，使业务契约、应用实现与部署配置能够在同一个 commit 中一致演进：

- `forge-web`：面向用户的 Next.js 工作台，只通过 `forge-server` 访问业务能力。
- `forge-server`：Spring Boot 模块化单体，是业务事实、授权和事务的唯一权威。
- `forge-agent`：FastAPI Agent Runtime，只能通过受控 Tool API 调用 `forge-server`。

当前仓库处于 P0 工程基础阶段。`forge-server` 已具备 Java 21/Spring Boot 模块化单体骨架；Web、Agent、业务领域模型和数据基础设施将在后续开发会话中逐步加入。

## 开发入口

```bash
make help
make ci
```

`make help` 列出公开命令；`make ci` 运行当前阶段已经具备的全部质量门禁。各命令是薄封装，实际检查位于 `scripts/`，可直接运行和审查。

Backend 可单独验证和启动：

```bash
./forge-server/mvnw -f forge-server/pom.xml test
./forge-server/mvnw -f forge-server/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

启动后可访问 `/actuator/health`、`/api/v1/system/status`、`/v3/api-docs`；开发 profile 额外开放 `/swagger-ui/index.html`。

## 文档

- 产品与设计基线位于 `docs/design/`。
- 分阶段开发指南位于 `docs/development/`。
- 架构决策记录位于 `docs/adr/`。
- 贡献和质量要求见 `CONTRIBUTING.md` 与 `AGENTS.md`。

## 架构边界

Browser 通过统一入口访问 Web 和 Server。Web 不直连 Agent、数据库、向量库或 GitLab；Agent 不直接访问业务 MySQL，也不持有业务 Secret。所有业务写操作和权限判断最终由 `forge-server` 执行。
