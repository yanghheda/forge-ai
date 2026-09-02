# forge-server

ForgeAI 的 Spring Boot 模块化单体，是业务事实、授权、状态机和事务的唯一权威。跨模块协作应通过公开应用服务或领域事件完成，Controller 不得直接访问 Repository。

## 本地命令

从仓库根目录执行：

```bash
./forge-server/mvnw -f forge-server/pom.xml test
./forge-server/mvnw -f forge-server/pom.xml package
./forge-server/mvnw -f forge-server/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

默认 profile 提供健康检查、公开系统状态和 OpenAPI JSON；Swagger UI 默认关闭，仅在 `dev` profile 开启。

## 模块与分层

业务模块使用 `ai.forge.server.<module>` 顶层包。模块内部依赖方向为：

```text
controller/infrastructure → application → domain
repository implementation → repository port ← application
```

跨模块写入只能调用目标模块的公开应用服务或发布领域事件，不能读取对方 Repository。当前 `system` 是用于验证分层和 HTTP 基础设施的最小只读模块，不包含业务 Entity、Repository 或数据库事务。

`platform` 只承载 HTTP requestId、统一错误和 OpenAPI 等技术横切能力；不得演变为存放业务规则的万能公共包。

## 当前公开入口

- `GET /actuator/health`：运维健康检查，不进入公开 OpenAPI。
- `GET /api/v1/system/status`：验证 Controller → Application → Domain 调用链。
- `GET /v3/api-docs`：公开 REST 契约来源，只收录 `/api/v1/**`。
- `GET /swagger-ui/index.html`：仅 `dev` profile 可用。

## 自动化边界

- ArchUnit 阻止 Controller 访问 Repository、应用层反向依赖和领域层依赖框架。
- Swagger 注解只允许出现在 Controller。
- JavaParser 扫描生产源码，阻止普通字段、record 组件、enum 常量和 enum 成员字段缺少独立中文普通块注释。
