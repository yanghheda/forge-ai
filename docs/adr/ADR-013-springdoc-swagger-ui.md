# ADR-013：使用 springdoc-openapi 与 Swagger UI

- 状态：Accepted
- 日期：2026-09-02

## 上下文

ADR-011 要求 Server 提供可生成、可检查的 OpenAPI 契约。开发阶段还需要一个轻量的契约浏览和接口联调入口。接口说明若散落在 DTO、Entity 或手写文档中，会与实际 Controller 行为漂移，也可能泄露内部结构。

## 决策

`forge-server` 使用 `springdoc-openapi` 从公开 Controller 生成 OpenAPI，并在开发环境提供 Swagger UI。Swagger/OpenAPI 描述注解只允许出现在 Controller；Entity、领域模型和普通 DTO 不承载 Swagger 注解。

内部 Agent API 与公开业务 API 必须分组或隔离，生产环境是否暴露 Swagger UI 由部署配置决定，默认不将其作为无保护的公网入口。

## 后果

- 实现、契约导出和开发联调入口使用同一来源。
- Controller 注解需要保持简洁，并通过测试验证公开范围和扩展字段。
- Swagger UI 只是契约可视化工具，不代表接口已经具备授权、安全或业务正确性。
- 会话 02 需要验证 `/v3/api-docs`、开发环境 UI 和公开/内部接口边界。

## 替代方案

- 手写静态 OpenAPI：暂不采用，因为更容易与 Controller 漂移。
- 在 Entity/DTO 上广泛添加 Swagger 注解：拒绝，因为会污染领域边界并暴露内部模型。
- 不提供契约 UI：不采用，因为会降低开发期可发现性与联调效率。

