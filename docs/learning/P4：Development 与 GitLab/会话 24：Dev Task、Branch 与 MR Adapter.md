# 会话 24：Dev Task、Branch 与 MR Adapter

## 我完成了什么

- 仅允许从 `READY_FOR_DEV` Requirement 创建带 `parent_id` 的 Dev Task。
- 新增 Branch、Merge Request 标准化快照，以及外部写操作记录；所有表和字段带中文 MySQL `COMMENT`。
- 扩展 `SourceControlProvider`，实现 GitLab Branch/MR 查询与创建，GitLab JSON 不进入核心工作流。
- 实现 `START_DEVELOPMENT` 编排：生成安全分支名、复用远端资源、检测同名异 SHA、timeout 后 reconcile，并在成功后关联 Work Item。
- 新增服务端 API 和 Requirement 页面中的 Dev Task 创建入口；Pipeline/Webhook 未实现。

## 核心设计

GitLab 是 Branch、MR 状态与提交 SHA 的最终事实来源。ForgeAI 的 `branches`、`merge_requests` 只是带 Work Item 关联的标准化缓存，不凭本地缓存宣称 MR 已合并。应用服务依赖 SPI，不依赖 GitLab 响应类型。

远端 API 缺少可依赖的强幂等语义，因此先写 `source_control_operations(PROCESSING)`。重试先按分支名以及 source/target/open MR 查询远端：同基准 SHA 的分支和已有 open MR 可复用；同名但 SHA 不同返回 `REMOTE_RESOURCE_CONFLICT`；timeout 后也先 reconcile，不直接再次 POST。

## 调用链

创建 Task：Web → `DevelopmentController` → `task.create` → 校验 Requirement 类型与 `READY_FOR_DEV` → `WorkItemStore.createChild` 原子编号与 parent 关联。

启动开发：Web/API → `task.edit`、`repo.read` → MyBatis 短事务读取同 Workspace/Project 的 Task、Requirement、Repo、Connection、Secret 并记录 `PROCESSING` → 事务外 GitLab 查询/创建 Branch → 查询/创建 MR → MyBatis 短事务 upsert 快照、关联 Task、推进 Task/Requirement 状态并完成操作记录。

## 数据与事务边界

- MySQL 是 Work Item 状态、幂等意图和本地关联事实来源；GitLab 是 Branch/MR 最终状态来源。
- 数据库事务内不执行 DNS、HTTP 或 GitLab JSON 解析，不持锁等待外部系统。
- Token 从密文临时解密，只进入当前 Adapter 调用栈；`DevelopmentContext.toString()` 明确排除 Token。
- 完成事务同时写 Branch/MR 关联，并把 Dev Task 推进到 `IN_PROGRESS`、Requirement 推进到 `IN_DEVELOPMENT`。

## 验证证据

- `DevelopmentServiceTest` 6 项通过：正常创建、已有资源复用、同名异 SHA、timeout reconcile、429 保留意图、越权先于 Store 访问。
- forge-web Work Item API 测试 2 项通过，TypeScript 类型检查通过。
- `FlywayMigrationIntegrationTest` 13 项通过，确认 V18、三张新表及表/字段中文注释。
- `make ci` 全量通过：forge-server 189 项、forge-web 26 项、forge-agent 37 项，并完成三应用构建。

## 风险与遗留

- 当前 `PROCESSING` 记录没有后台过期扫描；需要调用方用同一幂等键重试来触发 reconcile。
- GitLab 返回的 MR 状态只是同步快照；Pipeline/Webhook 驱动刷新属于会话 25，本轮不提前实现。
- 真正并发的两个不同幂等键可能同时命中同一远端分支；当前会在远端 409 后查询并核对 SHA，但仍需依靠命名和业务授权避免不相关任务争用同一分支名。
- 前端本轮提供 Dev Task 创建入口；Dev Task 独立详情页及启动表单可在不跨越 Pipeline 范围的后续 UI 增量中完善。

## 3 个复盘问题

1. Adapter 与直接在应用服务中调用 GitLab HTTP Client 的边界差异是什么？
2. 为什么 ForgeAI 可以保存 MR 快照，却不能据此宣称 MR 的最终状态？
3. 当远端不支持幂等键且 POST timeout 时，为什么必须先 reconcile，仍无法确认时应如何处理？
