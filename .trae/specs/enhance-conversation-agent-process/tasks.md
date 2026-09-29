# Tasks

- [x] Task 1: 后端内部工具事件端点与持久化
  - [x] SubTask 1.1: `AgentRunStore.appendToolEvent(organizationId, runId, callId, toolName, event, status, summary, durationMs)` 接口 + `MybatisAgentRunStore` 实现：沿用 `lockRun` + `advanceRunningSequence`，要求 Run 处于 `RUNNING`；按 `event` 取值写 `tool.started` / `tool.completed` / `tool.failed`，payload 含 `callId`、`name`、`status`、`summary`（可空）、`durationMs`（可空）；校验 `event` 取值合法、`callId`/`toolName` 非空与长度上限
  - [x] SubTask 1.2: `InternalAgentEventController` 新增 `POST /{runId}/tool-events`，复用私有 `credential(runId, authorization)`，保持 401 / 403 语义与既有端点一致；新增 `ToolEventRequest` 记录
  - [x] SubTask 1.3: `MybatisAgentRunStore.complete()` 移除循环内 `tool.completed` / `tool.failed` 事件写入，保留 `insertCompletedToolStep` 工具投影并保持序号分配正确
  - [x] SubTask 1.4: 集成测试：运行中追加工具事件并前推序号、非法 event 返回 400、缺失 token 返回 401、Run 不匹配返回 403；修正受 `complete()` 变更影响的既有断言

- [x] Task 2: 轨迹聚合按 callId 合并工具节点
  - [x] SubTask 2.1: `AgentTraceAggregator` TOOL 分支改为按 `callId` 复用/更新节点：`tool.started` 创建或更新 `RUNNING` 节点；`tool.completed` / `tool.failed` 把同 `callId` 节点收敛为终态并补全已持久化调用的参数、结果、风险等级与起止时间（优先按 `toolCallId` 匹配，其次按工具名 FIFO），并标记该持久化调用已消费
  - [x] SubTask 2.2: 保持无 `callId` 事件的原有 FIFO 行为，以及未消费持久化调用在末尾补节点的兜底
  - [x] SubTask 2.3: `AgentTraceAggregatorTest` 新增用例：started→completed 合并为单节点、仅 started 为 RUNNING、重复 started 幂等、无 callId 兼容、持久化兜底

- [x] Task 3: forge-agent 运行中工具事件上报
  - [x] SubTask 3.1: `AnswerStream` 协议新增 `publish_tool(...)`；`ServerAnswerStream` 实现 POST `/internal/v1/agent-runs/{runId}/tool-events`，携带 Run 凭据，请求体含 `callId`、`toolName`、`event`、`status`、`summary`、`durationMs`（复用现有 `_publish` 的鉴权与错误包装风格）
  - [x] SubTask 3.2: `runtime.py` 在 `_execute_tool` 调用 `transport.execute` 前发布 `tool.started`，返回后按归一化状态发布 `tool.completed` / `tool.failed`（含 `durationMs`）；`WAITING_APPROVAL` 时不发布终态；上报异常只记录告警，不影响 Run 结果
  - [x] SubTask 3.3: forge-agent 测试：发布顺序与字段、等待审批不发终态、上报失败不影响 Run；`tool_transport` 测试请求路径与请求体

- [x] Task 4: 前端轨迹模型补充跨 feature 导出
  - [x] SubTask 4.1: 把 `statusLabel` 与状态标签表从 `trace-node-card.tsx` 移入 `model/trace-model.ts`，`trace-node-card.tsx` 改为引用，行为不变
  - [x] SubTask 4.2: `features/agent-run/index.ts` 导出 `statusLabel`、`readText`、`readNumber`、`readTextList`、`formatDuration`、`formatNodeTime`，供会话面板跨 feature 复用（保持只经公开 index 导入）
  - [x] SubTask 4.3: 更新/补充 `trace-model.test.ts` 覆盖 `statusLabel`

- [x] Task 5: 会话面板内联执行过程渲染
  - [x] SubTask 5.1: 新增 `features/console/components/agent-process-nodes.tsx`（`AgentProcessNodes`）：按 `nodePresentation` 分派，紧凑内联渲染 `THINKING` / `REASONING` 折叠文本、`PLAN` 步骤列表、`STEP` 行、`TOOL` 卡片（工具名 · 状态 · 耗时 + 可展开参数/结果）、`APPROVAL`、未知 kind 的 `EVENT` 兜底；过滤 `ANSWER` / `TERMINAL`；提供空态
  - [x] SubTask 5.2: 在 `console.module.css` 增加与设计稿一致的紧凑内联样式（节点、状态徽标、可折叠文本、工具卡片、参数键值、JSON 块）
  - [x] SubTask 5.3: `agent-conversation-panel.tsx` 改为查询 `getRunTrace`（`["run-trace", effectiveRunId]`，非终态时 5 秒轮询），用 `AgentProcessNodes` 替换 `ReasoningDetails`，历史会话与进行中 Run 渲染一致；保留流式回答与审批卡片
  - [x] SubTask 5.4: `use-conversation-stream` 去掉扁平 `reasoning` 累积，改为在轨迹相关事件到达时去抖失效 `["run-trace", runId]`；保留回答增量与 Run 状态缓存更新、终态收敛逻辑
  - [x] SubTask 5.5: 移除 `agent-message.tsx` 中已无引用的 `ReasoningDetails` 及其测试；新增 `agent-process-nodes.test.tsx`（各 kind 渲染、过滤 ANSWER/TERMINAL、未知兜底、空态）与 `use-conversation-stream` 测试（回答累积、工具/思考事件触发轨迹刷新）
  - [x] SubTask 5.6: forge-web `lint`、`lint:boundaries`、`format:check`、`typecheck` 通过且无残留引用

- [x] Task 6: 文档同步与全量质量门禁
  - [x] SubTask 6.1: 更新 `docs/design/ForgeAI_详细设计_v1.0.md` 第 10 章，登记内部端点 `POST /internal/v1/agent-runs/{runId}/tool-events` 与 `tool.*` 事件改由运行中上报的说明
  - [x] SubTask 6.2: 运行 `make ci`（format-check、lint、contracts-check、test、build）与 `git diff --check`，修复全部失败项

# Task Dependencies

- Task 3 依赖 Task 1（需要内部端点契约）；Task 2 依赖 Task 1（需要事件 payload 契约）
- Task 1、Task 4 之间无依赖，可并行
- Task 2、Task 3 在 Task 1 完成后可并行
- Task 5 依赖 Task 4（跨 feature 复用导出）；Task 5.3 的正确性以 Task 2 的聚合契约为准
- Task 6 依赖 Task 1–5 全部完成
