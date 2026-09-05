# 会话 26：Development → QA Guard 与开发页面闭环

## 我完成了什么

- 为 Requirement 注册固定动作 `SUBMIT_FOR_QA`：`IN_DEVELOPMENT → READY_FOR_QA`，权限为 `development.submit`。
- 新增项目策略 `ciRequired`，默认开启；关闭时仍要求所有 Dev Task 完成，但不要求仓库、MR 或 Pipeline。
- 新增确定性 `DevelopmentQaGuard`，返回机器可读 `missing[]`，区分缺任务、任务未完成、缺仓库、缺 MR、缺 Pipeline、运行中、失败和 head SHA 不匹配。
- 新增 Development 汇总查询，将 Dev Task、Branch、MR、最新 Pipeline、策略和同步时间投影到一个只读响应。
- 新增 Dev Task 完成入口，使用 `expectedVersion` 与父 Requirement 状态约束完成原子更新。
- Requirement 页面补齐开发闭环：创建任务、启动开发、完成任务、查看 MR/Pipeline 快照并提交 QA。
- 保留 Webhook 与状态推进解耦：Webhook 只同步外部快照，用户显式重试 `SUBMIT_FOR_QA` 后才推进内部工作流。

## 核心设计

### 调用链

开发页面读取：

`RequirementDetail → DevelopmentPanel → DevelopmentController → DevelopmentQaQuery → MybatisDevelopmentQaStore → MySQL 快照`

提交 QA：

`RequirementDetail → WorkItemController → RequirementTransitionService → RequirementWorkflowRegistry → DevelopmentQaGuard → DevelopmentQaStore → RequirementTransitionStore`

外部状态更新仍保持：

`GitLab Webhook → WebhookService/Processor → MR/Pipeline 本地快照`

它不会直接调用 Requirement Transition。

### 策略与 Guard

- 所有路径都至少要求一个 Dev Task，且所有直属 Dev Task 必须为 `DONE`。
- `ciRequired=false` 时，到此即可进入 QA，适用于显式配置的无仓库或 CI optional 项目。
- `ciRequired=true` 时，项目必须有 ACTIVE 仓库，每个 Dev Task 必须关联 MR，并存在对应 MR 当前 `headSha` 的成功 Pipeline。
- “存在一个成功 Pipeline”不够，因为它可能验证的是旧提交；Guard 同时比较 `mergeRequestHeadSha` 与 `pipelineCommitSha`。
- 页面展示与 Guard 使用同一个汇总端口，避免 UI 提示与服务端放行规则漂移。

### 数据与事务边界

- MySQL 中的 Project Policy、Work Item、Branch、MR 与 Pipeline 快照是本次判定输入。
- Guard 只读且无副作用，不调用 GitLab，不持有外部请求期间的数据库锁。
- Dev Task 完成是单条带 Workspace、Project、父 Requirement 状态和版本条件的更新事务。
- Requirement 状态、版本与追加事件继续由既有 `RequirementTransitionStore` 在同一事务中写入。
- 并发提交使用 Requirement `expectedVersion` 竞争，只有一个请求能从 `IN_DEVELOPMENT` 写入 `READY_FOR_QA`。
- Webhook 快照是最终一致缓存；`lastSyncedAt` 暴露给页面，使用者能看见数据新鲜度而不是误认为实时强一致。

## 关键失败路径

- 无 Dev Task：`devTask`。
- 存在非 `DONE` Dev Task：`devTaskIncomplete`。
- CI required 但无 ACTIVE 仓库：`repository`。
- Dev Task 无 MR：`mergeRequest`。
- MR 无 Pipeline：`pipeline`。
- Pipeline 尚在 created/pending/running：`pipelineRunning`。
- Pipeline 终态非 success：`pipelineFailed`。
- 成功 Pipeline 的 commit 与 MR 当前 head 不同：`pipelineHeadMismatch`。
- Dev Task 完成或 Requirement 提交使用过期版本：`409`，不会部分写入。

## 测试证据

- `DevelopmentQaGuardTest`：覆盖无任务、任务未完成、无仓库、无 MR、无 Pipeline、运行中、失败、旧 SHA、CI optional 和成功路径。
- `RequirementWorkflowRegistryTest`：验证 `SUBMIT_FOR_QA` 的固定 From/To、权限与 Guard。
- `DevelopmentServiceTest`：验证 Dev Task 完成调用携带版本与项目范围。
- `RequirementTransitionIntegrationTest`（MySQL 8.4 Testcontainers）：验证 V20、无仓库提示、汇总 API、CI optional 放行、旧 commit 成功 Pipeline 拒绝、Dev Task 完成和并发提交唯一胜者。
- Web `work-item-api.test.ts` 与 `development-panel.test.tsx`：验证汇总/完成 API 路径、旧 commit 风险展示和任务完成交互。
- Web TypeScript 与 ESLint 检查通过。

## 风险与遗留问题

- Pipeline/MR 是最终一致快照；Webhook 延迟期间可能暂时拒绝刚刚已在 GitLab 成功的提交，但不会误放行。会话 27 再实现定时 reconcile 与故障恢复。
- 当前选取 MR 最近同步的 Pipeline；若 GitLab 同一 head 同时存在多条 Pipeline，最新一条失败会阻断，即使更早一条成功。这是保守策略，后续如需按 pipeline source 或 required jobs 细分应先扩展项目策略。
- 项目策略默认 `ciRequired=true`；无 GitLab 项目必须由管理员显式关闭，避免默认绕过质量门禁。
- 当前页面仍用短轮询刷新快照，没有新增通用业务 SSE。
- 本轮不创建 Test Case、Test Run 或 QA 页面规则；这些属于会话 28。

## 复盘问题

1. 为什么一个历史成功 Pipeline 不能证明 MR 当前代码可进入 QA，Guard 还必须比较哪些事实？
2. `ciRequired` 项目策略为什么应该决定是否需要 CI，而“所有 Dev Task 完成”仍然是不可关闭的基础规则？
3. 为什么 Webhook 只更新外部快照、由显式 `SUBMIT_FOR_QA` 事务推进状态，比 Webhook 直接修改 Requirement 更安全？
