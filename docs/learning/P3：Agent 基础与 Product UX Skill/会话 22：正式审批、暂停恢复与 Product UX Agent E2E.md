# 会话 22：正式审批、暂停/恢复与 Product/UX Agent E2E

## 我完成了什么

- 新增 `approvals` 权威表，冻结 Tool 名称、契约版本、参数 hash、AES-GCM 加密参数、资源版本、TTL、发起人与审批人；`agent_tool_calls` 支持 `WAITING_APPROVAL`。
- `ASK` 策略不再返回瞬时确认结果：Server 在同一事务创建审批与 Tool Call、推进 Run，并追加 `approval.required` 事件。
- 新增审批读取与决策 API。审批人必须拥有 `approval.decide`，且不得审批自己发起的调用；决策使用 `expectedVersion` 乐观锁。
- 批准事务只写 `AGENT_RUN_RESUME_REQUESTED` Outbox；消费者重新签发短时 Run credential，唤醒 forge-agent 的 SQLite checkpoint。
- forge-agent 在 `WAITING_APPROVAL` 时停在 Tool 观察之前；恢复沿用原 `toolCallId` 和参数，成功后才写观察并生成最终回答。
- 恢复执行前重新校验当前权限、Tool Contract 版本、参数 hash、审批 TTL 和资源版本；业务副作用与 Tool Call 成功结果仍在同一事务提交。
- Web Run 页面新增可刷新恢复的审批卡片，只展示脱敏摘要、风险、到期时间和资源版本，并提供批准/拒绝操作。

## 调用链

`forge-agent select/guard` → `forge-server internal Tool API` → Registry/Schema/最新权限 → 创建冻结审批与 checkpoint 暂停 → Web 读取审批 → 决策事务与 Resume Outbox → Worker → 新 credential → checkpoint 恢复 → 再校验冻结事实 → 应用服务副作用 → 幂等 Tool Call 结果 → Trace/SSE 完成。

## 数据与事务边界

- MySQL 是 Run、Tool Call、Approval、Outbox 与 SSE Event 的事实来源；浏览器状态不是审批事实。
- SQLite checkpoint 只保存 Agent 图恢复状态，不决定权限、审批或业务成功。
- 审批创建、Run 暂停与 `approval.required` 同一事务；批准与 Resume Outbox 同一事务。
- 恢复 Worker 的 HTTP 调用不持有数据库行锁。最终 Tool 副作用与成功幂等记录在本地事务提交。
- 完整参数仅以 AES-GCM 密文保存；API 和事件只暴露 hash 与必要元数据。

## 验证证据

- forge-agent：37 个测试通过；新增用例覆盖等待审批、新 Runtime 进程恢复、原 Tool Call ID 与新 credential。
- forge-web：12 个测试文件、24 个测试通过；TypeScript 类型检查与 ESLint 通过。
- forge-server：编译、架构门禁与审批加密单测通过；MySQL/Testcontainers 的 20 个 Internal Tool 集成测试通过，覆盖审批冻结、成功重放、参数变化和资源版本变化。

## 风险与遗留

- 当前 Resume Worker 适合单实例开发基线；多 Worker 部署前需要租约式领取，避免重复 HTTP 唤醒造成无效负载。Tool 幂等仍能阻止重复业务写。
- TTL 目前在决策和恢复路径惰性判定；独立的批量过期清理与取消 API 尚需按后续范围确认，不应在本轮擅自扩展运维策略。
- Fake Language Model 只验证确定性 Product Tool 路径；真实模型语言质量仍由 16.4 的 Agent Eval 数据集持续评估。

## 3 个复盘问题

1. 为什么批准事务只写 Outbox，而不能在持有审批行锁时直接调用 forge-agent？
2. 为什么恢复时既要比较参数 hash，也要比较 Tool Contract 与资源版本？
3. SQLite checkpoint 已记录“等待审批”时，为什么仍不能把它当作审批和权限的事实来源？
