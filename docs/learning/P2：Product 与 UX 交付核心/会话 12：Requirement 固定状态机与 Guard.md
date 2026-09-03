# 会话 12：Requirement 固定状态机与 Guard

## 我完成了什么

- Requirement 的完整状态词汇由服务端枚举固定，客户端不能提交任意目标状态。
- 本轮实现首批 Product Actions：`SUBMIT_PRODUCT_REVIEW` 与 `REJECT_PRODUCT_REVIEW`。
- Workflow Registry 为每个 Action 固定类型、From、To、权限和无副作用 Guard。
- 提交产品评审要求目标、纳入范围和至少一条验收标准；退回产品评审要求 reason。
- Transition 使用 `expectedVersion` 和 Work Item 范围内的 `idempotencyKey`。
- 状态、version、评审记录和活动事件在一个 MySQL 本地事务内提交。
- 活动时间线按事件 id 升序读取，并重新执行资源 scope 与 read 权限检查。

入口到结果的调用链：

```text
Cookie Session + CSRF
  → WorkItemController（只接收 Action，不接收 targetStatus）
  → RequirementTransitionService
  → scoped Work Item + 幂等事实
  → RequirementWorkflowRegistry
  → PermissionEvaluator
  → RequirementMaterialGuard / ReasonRequiredGuard
  → RequirementTransitionStore
  → MyBatis UPDATE + review_records + work_item_events
  → MySQL COMMIT
```

## 我理解的核心设计

### 状态、命令与事件不同

- 状态是当前事实，例如 `work_items.status = PRODUCT_REVIEW`。
- 命令是调用方意图，例如 `SUBMIT_PRODUCT_REVIEW`；它可能因权限、旧状态、Guard 或版本冲突失败。
- 事件是已经成功发生的历史，例如同名的 `work_item_events.event_type`；只有事务成功后才存在。

API 接收 Action 而不是 targetStatus，使服务端能够固定每条边的权限、Guard 和副作用。若客户端直接提交 `PRODUCT_REVIEW`，服务端无法知道它是在提交评审、管理员修复还是绕过流程。

### Guard 为什么确定且无副作用

Guard 只读取 MySQL 事实并返回机器可读 `missing[]`。相同事实产生相同结果；它不写评审、不发事件、不调用外部系统。这样 Guard 失败天然不会留下半成品，测试也能逐项验证缺少 `goal`、`inScope` 或 `acceptanceCriteria`。

权限检查位于 Guard 之前。无权用户先得到统一 404，不能借 `missing[]` 探测需求材料。幂等重放同样重新检查原 Action 权限，避免用户权限撤销后继续读取旧写结果。

### 数据与事务边界

`work_items.status` 是当前状态事实源，`work_item_events` 是追加历史，不通过事件回放计算当前状态。最终 UPDATE 同时匹配 Workspace、Project、Work Item、REQUIREMENT 类型、From 状态和 expectedVersion，并原子增加 version。

状态 UPDATE、`review_records` 和 `work_item_events` 由应用服务事务包围。测试故意让评审记录写入因字段超长失败，证明此前执行的状态和 version 更新一起回滚。本轮事务没有 Redis、Qdrant、Agent 或外部网络调用。

### 并发与幂等

两个不同请求使用同一 expectedVersion 时，只有一个 UPDATE 能命中，另一个得到 `VERSION_CONFLICT`，失败方不会留下评审或事件。同一 Work Item 的幂等键具有数据库唯一约束；同 key、同 Action 重试返回首次事件中固化的状态、version 和 eventId，同 key、不同 Action 返回 `IDEMPOTENCY_CONFLICT`。

## 失败路径

- 非法 From/Action：Registry 拒绝，返回 `INVALID_TRANSITION`。
- 缺结构化材料：返回 `422 WORKFLOW_GUARD_FAILED` 和 `missing[]`，version 不增加。
- 产品退回无 reason：Guard 拒绝，不新增评审或事件。
- expectedVersion 过期：最终 UPDATE 不命中，返回 `VERSION_CONFLICT`。
- 幂等键跨 Action 复用：返回 `IDEMPOTENCY_CONFLICT`。
- 评审或事件写入失败：整个状态事务回滚。
- 错误 Workspace/Project 或无权限：统一返回 404，避免资源枚举。

## 测试证据

- `RequirementWorkflowRegistryTest`：固定两条 Product 转换、权限非空、目标可达、所有非法 From/Action 组合及 Task 类型拒绝。
- `RequirementTransitionIntegrationTest.missingMaterialsReturnMachineReadableGuardFailureWithoutAnyWrite`：真实 MySQL Guard 失败无写入。
- `submitAndRejectWriteStateReviewAndEventsInTheSameTransactions`：状态、version、评审与活动时间线一致。
- `missingRejectReasonDoesNotIncrementVersionOrAppendHistory`：reason Guard 无副作用。
- `retryWithTheSameKeyReturnsTheOriginalResultWithoutDuplicateWrites`：幂等重试不重复写。
- `reusingAnIdempotencyKeyForAnotherActionIsRejected`：禁止跨 Action 复用 key。
- `concurrentTransitionsWithOneVersionHaveExactlyOneWinner`：并发转换只有一个成功事件和评审记录。
- `failedReviewOrEventWriteRollsBackTheStatusAndVersion`：后续写失败时状态回滚。
- `eventTimelineCannotCrossTheRequestedScope`：活动时间线不能跨 Project scope。
- `FlywayMigrationIntegrationTest.requirementWorkflowMigrationCreatesGuardReviewAndEventFacts`：V8 表、索引、权限和中文数据库注释存在。

## 遗留问题

- `APPROVE_PRODUCT_REVIEW` 依赖已发布 PRD 和 UX Task，留到会话 13–15，不以空 Guard 提前实现。
- Requirement Details 本轮只有数据库事实和只读 Guard 端口；正式编辑 API 与 Product 页面属于会话 14。
- UX、Development、QA、Release、CANCEL、BLOCK/UNBLOCK 动作未注册，按后续会话逐步实现。
- 当前活动 API 只覆盖本轮工作流事件，评论、关系和其他 Activity 类型尚未聚合。

## 3 个复盘问题

1. 为什么不用通用工作流引擎？
   - 当前流程固定、动作数量有限，代码 Registry 更容易静态审查权限、租户 scope、Guard 和事务副作用。通用引擎会过早引入运行时配置、迁移和脚本安全问题，却没有对应的动态流程需求。
2. Guard 与权限检查顺序为何重要？
   - 先检查权限，才能避免无权用户通过详细 Guard 结果推断目标、范围或交付物是否存在；资源和权限不可见统一为 404。Guard 只对已经有权执行动作的主体解释缺失条件。
3. 事件和当前状态谁是事实源？
   - `work_items.status` 是当前状态事实源，事件是同事务追加的历史和活动时间线。本项目没有采用 Event Sourcing，因此不能只靠回放 `work_item_events` 决定当前状态。
