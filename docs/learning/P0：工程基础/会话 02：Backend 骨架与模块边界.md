# 会话 02：Backend 骨架与模块边界

## 我完成了什么

- 用户可见结果：`forge-server` 可以启动，并提供公开的 `GET /api/v1/system/status`、Actuator 健康检查、OpenAPI 文档、统一错误信封和 requestId。
- 从入口到结果的调用链：HTTP 请求 → `RequestIdFilter.doFilterInternal()` → `SystemController.getStatus()` → `SystemStatusQuery.getStatus()` → `SystemStatus` → `SystemStatusResponse` → JSON 响应。
- 当前状态接口只证明 Controller、Application、Domain 的最小调用链成立，不访问数据库，也不代表真实业务功能已经完成。

## 我理解的核心设计

- 关键不变量：Controller 只负责协议适配和对外 API 描述，不能直接访问 Repository；应用层组织用例，领域层保持框架无关。
- 关键不变量：一个业务模块不能绕过目标模块的公开用例，直接访问另一个模块的 Repository。
- 关键不变量：Swagger/OpenAPI 注解只能出现在 Controller，领域类型不能为了生成 API 文档而依赖 Swagger。
- 模块化单体保留清晰模块边界和单进程事务能力，同时避免当前阶段不必要的服务发现、分布式事务和跨服务版本治理成本。
- `RequestIdFilter` 只接受符合安全格式的外部 requestId，否则生成新的 `req_...`，避免换行等输入污染结构化日志。
- `GlobalExceptionHandler` 将内部异常映射为稳定错误码和错误信封，不向调用方暴露堆栈。
- 事务/一致性边界：本轮没有 Repository 和业务写事务；后续事务应位于应用服务用例边界，而不是 Controller。
- 权限与安全边界：公开 OpenAPI 只匹配 `/api/v1/**`，不包含 Actuator 和未来的 `/internal/**`；Swagger UI 默认关闭，只在本地开发 profile 开启。
- 为什么不按全局 Controller/Service/Repository 分包：这种分包会把同一业务能力拆散，并让跨领域依赖更难被工具识别和阻断。

## 失败路径

- 触发方式：请求不存在的 API、提交非法参数、传入包含换行或非法字符的 `X-Request-ID`，或者在 Controller 中直接依赖 Repository。
- 系统如何失败：不存在的资源返回稳定的 `RESOURCE_NOT_FOUND`；参数错误返回 `VALIDATION_FAILED` 和字段详情；非法 requestId 被替换；架构违规在 ArchUnit 测试阶段失败。
- 数据是否保持正确：当前接口是只读占位，不改变数据；错误在进入未来业务写入前被转换或阻断。
- 如何定位与恢复：使用响应中的 requestId 查找 JSON 日志；架构失败则根据 ArchUnit 输出的源类、目标类和违反规则进行修复。

## 测试证据

- `ApiWebTest.systemStatusUsesControllerApplicationDomainChain`：验证状态接口、响应字段和 requestId 响应头。
- `ApiWebTest.acceptedRequestIdIsReturnedInHeaderAndJsonLog`：验证合法 requestId 同时出现在响应和结构化日志中。
- `ApiWebTest.unsafeRequestIdIsReplaced`：验证不安全 requestId 不会进入日志链路。
- `ApiWebTest.missingRouteReturnsStableErrorWithoutStackTrace`：验证稳定错误码且不泄露堆栈。
- `ModuleArchitectureTest.productionCodeRespectsLayerAndSwaggerBoundaries`：检查生产代码的层次、领域独立性、跨模块 Repository 和 Swagger 边界。
- 三个负向 fixture 测试证明 Controller→Repository、非 Controller Swagger 注解和跨模块 Repository 访问确实会被拒绝。
- 它不能证明什么：ArchUnit 只能检查已表达为依赖规则的静态结构，不能证明应用服务内部的业务逻辑正确，也不能阻止通过反射、HTTP 或不规范包名绕开语义边界。

## 仍不清楚的问题

- 后续模块之间哪些查询可以直接通过公开 Application API 调用，哪些应该使用领域事件或 Outbox 解耦？
- requestId 在 Server→Agent→外部系统的完整链路中应如何保持一致，而不是由每一跳重新生成？

