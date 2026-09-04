# 会话 19：Agent Gateway、LangGraph 最小图与 Checkpoint

## 我完成了什么

- `forge-server` 在 Agent Run 创建事务提交后，通过 `AgentDispatcher` 和 `HttpAgentRuntimeGateway` 调用真实 FastAPI 内部启动端点；相同 `runId` 的重复启动由 Agent Checkpoint 幂等收敛。
- Backend 生成短时 `ContextManifest`，只包含已认证主体、Workspace/Project/Work Item scope、Product/UX Skill、预算和资源引用；本轮 `effectiveToolNames` 固定为空，没有提前实现业务 Tool。
- `forge-agent` 使用真实 LangGraph `StateGraph` 构建 `validate_context → create_plan → finalize` 最小有界图，Fake LLM 产生确定性计划和完成摘要。
- SQLiteSaver 以 `runId` 作为 thread ID 保存节点间 Checkpoint；Compose 使用独立数据卷，进程或容器重启后可以从失败节点前的 Checkpoint 恢复。

## 我理解的核心设计

- 调用链：用户 API 创建 `QUEUED` Run 与首事件 → 事务提交 → 异步 Dispatcher 将 Backend Run 推进为 `RUNNING` → Gateway 签发绑定 `run_id/workspace_id/project_id` 的短时 JWT 并 POST Agent → Agent 校验 JWT、路径 Run ID、Manifest Run ID 与过期时间 → LangGraph 执行或恢复 → Backend 用独立短事务写完成或失败事实。
- 权威边界：MySQL 中的 Run、Step、Event 和 SSE 序号仍是用户可见业务事实；SQLite Checkpoint 只保存 Agent 恢复状态，丢失后不能据此改写业务事实，更不能替代权限、状态机或审计。
- 事务边界：创建 Run 的事务不包含 HTTP 调用；Agent HTTP 调用不持有数据库行锁；开始、完成和失败分别由 `AgentRunStore` 用带 scope 的行锁短事务投影。
- 安全边界：内部端点没有 OpenAPI 暴露且必须持有 HS256 服务 JWT；Run 启动额外要求 JWT 的 `run_id` 与路径一致。Manifest 的 Tool 集合只是入口上限，不是执行授权；本轮为空。
- 图节点有确定边界，Checkpoint 才能明确记录“哪个节点已完成”，节点失败后的重试不会重新生成已经持久化的计划。

## 失败路径

- 缺失、错误签名、过期或绑定其他 Run 的 JWT 返回统一 401，不泄露具体校验原因。
- 路径 Run ID 与 Manifest Run ID 不同返回 409；Manifest 过期或无时区在进入图前失败。
- 图节点抛出异常时，SQLite 保留上一个成功节点的 Checkpoint；同一 Run 再次启动从待执行节点继续。
- Gateway、Agent 或响应契约失败时，Dispatcher 捕获异常并让 Backend 以 `AGENT_GATEWAY_FAILED` 结束 Run；错误细节只写服务日志。

## 测试证据

- `test_runtime.py`：验证计划/完成、重复 start 只执行一次、Manifest 过期拒绝、finalize 节点失败后由新 Runtime 实例恢复且不重跑 plan。
- `test_runs_api.py`：验证内部启动端点拒绝缺失、错误签名和过期 JWT，验证 Run ID 绑定、路径/Manifest 冲突及重复 HTTP start。
- `HttpAgentRuntimeGatewayTest`：验证 Backend 请求真实内部路径、携带 Bearer JWT，并发送 Backend 生成且 Tool 列表为空的 Manifest。
- `AgentRunIntegrationTest`：真实 MySQL 下回归 Run/Step/Event 同事务投影、幂等创建、scope 隔离和 SSE 重放。
- `make agent-test`：18 个 Agent 测试通过；定向 Server 测试 `HttpAgentRuntimeGatewayTest,AgentServiceTokenProviderTest` 通过；定向 MySQL 集成测试 7 个场景通过。
- 这些证据尚不能证明多 Agent 实例对同一 SQLite 文件的并发安全、长耗时 HTTP 的超时恢复、真实模型质量或业务 Tool 幂等；这些不在本轮范围。

## 仍不清楚的问题

- 下一轮以前不接 RAG；当前资源引用版本暂以 `0` 表示，未来应由 Backend 权威资源实际版本填充。
- 当前 Runtime 用进程内锁串行保护 SQLite；如果未来横向扩展多个 Agent 实例，需要外部租约或按 Run 分片，不能共享单个 SQLite 数据卷并假设全局互斥。
- Gateway 当前同步等待最小图结果；进入真实长任务前需要明确连接/读取超时、异步回写批次和可恢复调度策略。

## 3 个复盘问题

1. 为什么 Agent Checkpoint 即使保存了完整状态，也不能成为 Run 对外状态和业务授权的事实来源？
2. 为什么服务 JWT 除了短时有效，还必须绑定 `run_id`，而 Manifest 中的 Tool 名称仍不能直接视为授权？
3. 如果 `create_plan` 已完成而 `finalize` 失败，LangGraph Checkpoint 如何避免恢复时再次调用计划模型；节点边界若混在一个自由循环里会失去什么？
