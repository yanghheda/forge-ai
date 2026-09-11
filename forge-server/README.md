# forge-server

ForgeAI 的 Spring Boot 模块化单体，是业务事实、授权、状态机和事务的唯一权威。跨模块协作应通过公开应用服务或领域事件完成，Controller 不得直接访问 Repository。

## 本地命令

从仓库根目录执行：

```bash
./forge-server/mvnw -f forge-server/pom.xml test
./forge-server/mvnw -f forge-server/pom.xml package
./forge-server/mvnw -f forge-server/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

默认 profile 连接 Compose 网络中的 `mysql`、`redis` 和 `qdrant`。MySQL 与 Redis 决定 readiness；Qdrant 是可重建派生索引，故障时报告 `DEGRADED` 而不阻断 Backend readiness。Swagger UI 默认关闭，仅在 `dev` profile 开启。

Flyway 是 MySQL Schema 的唯一演进入口。当前尚未上线的历史迁移已压平为公司级 `V1__company_platform_baseline.sql`；从该基线开始，后续结构调整只能新增更高版本迁移。

## 模块与分层

业务模块使用 `ai.forge.server.<module>` 顶层包。模块内部依赖方向为：

```text
controller/infrastructure → application → domain
repository implementation → repository port ← application
```

跨模块写入只能调用目标模块的公开应用服务或发布领域事件，不能读取对方 Repository。当前 `system` 是用于验证分层和 HTTP 基础设施的最小只读模块，不包含业务 Entity、Repository 或数据库事务。

`platform` 只承载 HTTP requestId、统一错误和 OpenAPI 等技术横切能力；不得演变为存放业务规则的万能公共包。

## 登录与 Session

浏览器只持有名为 `FORGE_SESSION` 的不透明 Cookie；认证主体、首次签发时间和空闲过期由 Spring Session Redis 管理。默认空闲 30 分钟、绝对 12 小时，生产 Cookie 默认启用 `Secure`，`dev`/`test` profile 为 HTTP 联调显式关闭。可通过 `FORGE_SESSION_IDLE_TIMEOUT`、`FORGE_SESSION_ABSOLUTE_TIMEOUT` 和 `FORGE_SESSION_COOKIE_SECURE` 覆盖。

登录以“Servlet 解析后的远端地址 + 规范化邮箱”的 SHA-256 摘要作为 Redis 限流键，默认 5 分钟 10 次；账户连续失败默认 5 次后在 MySQL 锁定 15 分钟。阈值可分别通过 `FORGE_LOGIN_RATE_LIMIT_ATTEMPTS`、`FORGE_LOGIN_RATE_LIMIT_WINDOW`、`FORGE_FAILED_LOGIN_THRESHOLD` 和 `FORGE_ACCOUNT_LOCK_DURATION` 配置。部署在反向代理之后时，只有在代理信任边界配置完成后才应启用 forwarded header 解析。

## 当前公开入口

- `GET /actuator/health`：运维健康检查，不进入公开 OpenAPI。
- `GET /api/v1/system/status`：验证 Controller → Application → Domain 调用链。
- `POST /api/v1/setup/initialize`：未初始化实例原子创建首个 Owner、Organization 与 Workspace。
- `POST /api/v1/auth/login`：本地密码登录；成功后轮换 Session ID 并写入 Redis。
- `POST /api/v1/auth/logout`：撤销当前服务端 Session，不影响同用户其他会话。
- `GET /api/v1/me`：读取当前用户以及 MySQL 中实时有效的 Workspace/角色范围。
- `GET /v3/api-docs`：公开 REST 契约来源，只收录 `/api/v1/**`。
- `GET /swagger-ui/index.html`：仅 `dev` profile 可用。

## 自动化边界

- ArchUnit 阻止 Controller 访问 Repository、应用层反向依赖和领域层依赖框架。
- Swagger 注解只允许出现在 Controller。
- JavaParser 扫描生产源码，阻止普通字段、record 组件、enum 常量和 enum 成员字段缺少独立中文普通块注释。
