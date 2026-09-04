# 会话 18：Agent Run、Trace 与 SSE 持久事件

## 我完成了什么

- 用户可见结果：可以创建 Product/UX Agent Run，查看 Backend 权威快照，并在 Run 页面通过 SSE 看到可恢复的步骤时间线。当前执行器明确是 Fake Runner，不调用 LLM。
- 从入口到结果的调用链：`POST /api/v1/agent-runs` → `AgentRunController` → `AgentRunService` 做项目、Skill 和可选 Work Item 授权 → `MybatisAgentRunStore` 在事务内写 `QUEUED` Run 与 `agent.queued` → 提交后 `FakeAgentDispatcher` 异步推进 Step/Event 和终态。Web 先调用快照 API，再用快照的 `lastSequence` 订阅 `/events`。

## 我理解的核心设计

- 关键不变量：Run 状态和可重放业务事件以 MySQL 为唯一事实来源；同一个 Run 的 `sequence` 连续递增；Run 状态、`last_sequence`、Step 变化和对应 Event 在同一事务提交；SSE 断开只销毁连接，不取消 Run。
- 事务/一致性边界：创建事务只负责 Run 与首个事件，提交后才调度 Fake Runner。开始和完成分别是短事务，每次先按 Workspace、Project、Run 行锁串行分配序号，再原子写状态、Step 和事件。SSE 是只读连接，先补发 MySQL 事件再短轮询；心跳不持久化，也不占业务序号。
- 权限与安全边界：入口同时要求 `agent.run` 和 Skill 对应权限；可选 Work Item 必须属于同一 Workspace/Project 且可读。所有 Run/Step/Event 查询和内部写入都显式携带 scope。原始 message 不入库，只保存字符数摘要；事件只含 UI 恢复字段，不保存隐式推理。
- 为什么没有选择另一种方案：SSE 符合服务端单向时间线，协议自带事件 ID 和重连语义，比 WebSocket 更简单。Redis Pub/Sub 只能作为未来的唤醒优化，不能承担事件事实；本轮数据量很小，MySQL 短轮询先保证正确性。

## 失败路径

- 触发方式：客户端提交错误 Workspace/Project、无 `agent.run`/Skill 权限、不可读 Work Item，或 SSE 提交非法/超前序号。
- 系统如何失败：资源 scope 和权限失败统一返回 404，避免资源枚举；非法游标返回 400；Fake Runner 未知异常被记录，并尝试把 Run 原子推进到 `FAILED` 和追加 `agent.failed`。
- 数据是否保持正确：每次状态推进先锁 Run；更新数量不符合预期就回滚事务。事件唯一键 `(run_id, sequence)` 和 Step 唯一键 `(run_id, step_no)` 提供数据库兜底。
- 如何定位与恢复：通过响应 `requestId`、事件 `requestId`、Run ID 和稳定 `errorCode` 关联日志。浏览器只从 reducer 的最后连续序号重连；终态仍以重新查询的 Run 快照为准。

## 测试证据

- `AgentRunIntegrationTest.createsRunAndFakeDispatcherPersistsOrderedTerminalTraceWithoutPromptBody`：真实 MySQL 下验证 ULID、5 个连续事件、Step 终态、Run 终态和原始正文不落库。
- `AgentRunIntegrationTest.clientRequestIdIsIdempotentForTheSameUserAndProject`：相同用户、项目、请求键只生成一个 Run。
- `AgentRunIntegrationTest.terminalStreamReplaysAfterLastEventIdAndThenCloses`：验证 `Last-Event-ID` 补发、已追平终态立即关闭，以及 SSE 正文不泄露输入。
- `AgentRunIntegrationTest.streamScopeViolationIsHiddenAsNotFound`：验证 SSE 与快照使用相同项目 scope。
- `run-reducer.test.ts`：验证重复事件幂等、跳号不推进连续游标、Step 投影和终态关闭。
- `FlywayMigrationIntegrationTest.agentRunMigrationCreatesCommentedReplayFactsAndPermission`：验证 V13 表、中文元数据注释和 `agent.run` 角色映射。
- 它们不能证明什么：当前没有高并发长 Run、代理/负载均衡缓冲、浏览器后台冻结或 Redis 唤醒压力数据；Fake Runner 也不能证明 Deep Agents、Checkpoint、Tool 或审批恢复正确。

## 仍不清楚的问题

- 进入真实 Agent Gateway 后，Step/Event 批量回写应采用多大的事务批次，才能兼顾重放延迟和数据库写放大？
- 多实例部署时，Redis 唤醒丢失后的最大短轮询间隔应如何用 SLO 反推？
- 事件保留和归档如何同时满足审计、成本与用户删除要求？

## 3 个复盘问题

1. 为什么“先建立 SSE 再查询快照”仍可能让 UI 暂时投影错误，正确顺序如何消除竞态？
2. 为什么 `last_sequence` 必须和对应事件在同一事务提交，分别提交会产生哪两种坏状态？
3. 为什么 reducer 遇到 `sequence=5` 而当前连续序号为 3 时不能直接接受 5，即使事件类型看起来可以独立渲染？
