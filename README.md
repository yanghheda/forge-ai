# ForgeAI

> AI-Native Software Delivery Workbench

ForgeAI is an open-source, self-hosted AI-native software delivery workbench. It connects Product, UX, Development, QA and Release workflows with AI Agents, enabling teams to manage requirements, documents, code collaboration, CI/CD and delivery automation in one workspace.

ForgeAI 采用 Monorepo 管理三个独立应用，使业务契约、应用实现与部署配置能够在同一个 commit 中一致演进：

- `forge-web`：面向用户的 Vite + React 工作台，只通过 `forge-server` 访问业务能力。
- `forge-server`：Spring Boot 模块化单体，是业务事实、授权和事务的唯一权威。
- `forge-agent`：FastAPI Agent Runtime，只能通过受控 Tool API 调用 `forge-server`。

当前仓库已实现需求交付全链路：三应用协作覆盖需求、UX、研发、测试与发布，外部 MySQL/Redis/Qdrant 接入、Tool/Skill 契约、Compose 部署与 Smoke 均已就绪。后续演进以公司级架构基线与最新 Accepted ADR 为准。

## 开发入口

```bash
make help
make ci
make smoke
```

`make help` 列出公开命令；`make ci` 运行当前阶段已经具备的全部质量门禁。各命令是薄封装，实际检查位于 `scripts/`，可直接运行和审查。

Backend 可单独验证和启动：

```bash
./forge-server/mvnw -f forge-server/pom.xml test
./forge-server/mvnw -f forge-server/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

启动后可访问 `/actuator/health`、`/api/v1/system/status`、`/v3/api-docs`；开发 profile 额外开放 `/swagger-ui/index.html`。

服务器上的 MySQL、Redis、Qdrant 由项目外的 `/opt/infra` 独立运行并提供外部网络 `infra-net`。
复制并修改 `deploy/.env.example` 后，执行 `make test-apps-up` 或 `make apps-up` 只启动三应用；
ForgeAI 的停止命令不会触碰共享基础设施。浏览器经 Web 的 `/api` 代理访问 Server，Server 使用短时服务 JWT 探测 Agent 内部就绪端点。

### 本地共享基础设施

本机开发复用同一套共享基础设施（`infra-mysql` / `infra-redis` / `infra-qdrant`，网络 `infra-net`），
不在本项目内自建 MySQL / Redis / Qdrant。接入方式见
`personal-static-web/deploy/infra/LOCAL_DEV.md`；`deploy/README.md` 记录了本项目的取值与隔离前缀。

## 文档

- 当前产品与工程事实以 `docs/design/ForgeAI_公司级架构基线_v2.md` 与 `docs/adr/` 中最新的 Accepted ADR 为准。
- 任务上下文路由见 `docs/context/INDEX.md`。
- 贡献和质量要求见 `CONTRIBUTING.md` 与 `AGENTS.md`。

## 架构边界

Browser 通过统一入口访问 Web 和 Server。Web 不直连 Agent、数据库、向量库或 GitLab；Agent 不直接访问业务 MySQL，也不持有业务 Secret。所有业务写操作和权限判断最终由 `forge-server` 执行。
