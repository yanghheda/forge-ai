# 会话面板 Agent 执行过程实时展示 Spec

## Why

会话面板当前把模型思考与执行计划合并成一个扁平的「思考」列表，且工具调用事件只在 Run 结束时批量补发，用户看不到像 Trae / ChatGPT 那样的「思考内容 → 调用工具 → 执行步骤 → 最终回答」完整执行过程。

## What Changes

- forge-agent 新增运行中工具事件上报：在 `_execute_tool` 调用工具前后发布 `tool.started` / `tool.completed` / `tool.failed`。
- forge-server 新增内部端点 `POST /internal/v1/agent-runs/{runId}/tool-events` 与 `AgentRunStore.appendToolEvent`，使运行中的 Run 即可追加工具事件。
- `MybatisAgentRunStore.complete()` 不再批量写 `tool.completed` / `tool.failed` 事件，避免与实时事件重复；保留 `agent_steps` 工具投影。
- `AgentTraceAggregator` 改为按 `callId` 合并同一工具调用的 `tool.started` 与 `tool.completed` / `tool.failed`，无 `callId` 时保持原有按工具名 FIFO 行为。
- forge-web 会话面板改为查询 `getRunTrace`，在 Agent 消息气泡流中内联渲染执行过程节点（模型思考、执行思路、计划、步骤、工具调用、审批）；进行中 Run 用 SSE 触发去抖刷新并以 5 秒轮询兜底，历史会话一次性完整呈现。
- 新增紧凑内联渲染组件（贴合 `docs/design/forgeai-console/pages/agent-command.html` 的工具卡片设计），移除仅承载单一「思考」列表的 `ReasoningDetails`。
- 同步 `docs/design/ForgeAI_详细设计_v1.0.md` 第 10 章事件契约。

**BREAKING**（内部契约）：工具事件不再由 `complete()` 批量补发，改由运行中实时上报。已持久化的 `agent_tool_calls` 兜底路径保证工具信息不丢失。

## Impact

- Affected specs：Agent 运行事件契约（`DETAIL` 10.1 / 10.2）、会话面板（`DETAIL` 13）、Agent Trace（`DETAIL` 9）。
- Affected code：
  - forge-agent：`src/forge_agent/gateway/runtime.py`、`src/forge_agent/gateway/tool_transport.py`
  - forge-server：`agent/controller/InternalAgentEventController.java`、`agent/application/AgentRunStore.java`、`agent/infrastructure/persistence/MybatisAgentRunStore.java`、`agent/application/AgentTraceAggregator.java`
  - forge-web：`features/console/components/agent-conversation-panel.tsx`、`features/console/components/agent-message.tsx`、`features/console/components/console.module.css`、`features/console/hooks/use-conversation-stream.ts`、新增 `features/console/components/agent-process-nodes.tsx`、`features/agent-run/model/trace-model.ts`、`features/agent-run/components/trace-node-card.tsx`、`features/agent-run/index.ts`

## ADDED Requirements

### Requirement: 运行中实时工具事件上报

系统 SHALL 在工具调用开始与结束时，经内部端点把 `tool.started` / `tool.completed` / `tool.failed` 事件写入权威 Run 事件流，供轨迹与会话面板实时展示。

事件 payload 统一包含 `callId`（Agent 侧调用标识）与 `name`（Tool 契约名）；`tool.started` 的 `status` 为 `RUNNING`；终态事件另含 `status`、`summary`、`durationMs`。

#### Scenario: 工具调用开始
- **WHEN** forge-agent 在处于 RUNNING 的 Run 内开始执行一个工具
- **THEN** Server 追加 `tool.started` 事件并前推 Run 的 `last_sequence`

#### Scenario: 工具调用成功
- **WHEN** 工具执行成功返回
- **THEN** Server 追加 `tool.completed` 事件，含 `status=SUCCEEDED`、`summary` 与 `durationMs`

#### Scenario: 工具调用失败
- **WHEN** 工具执行返回失败或传输失败
- **THEN** Server 追加 `tool.failed` 事件，`status=FAILED`，并含错误码摘要

#### Scenario: 等待审批不误报终态
- **WHEN** 工具执行返回 `WAITING_APPROVAL`（Run 转入人工审批）
- **THEN** Agent 不再发布该工具的终态事件，审批过程由 `approval.*` 事件承载

#### Scenario: 上报失败不影响 Run
- **WHEN** 工具事件上报出现网络或校验失败
- **THEN** Agent 只记录告警，工具执行结果与 Run 终态判定不受影响

#### Scenario: 凭据与绑定校验
- **WHEN** 内部端点收到缺失/非法 Run 凭据或 Run 不匹配的请求
- **THEN** 分别返回 401 / 403，且不写入任何事件

### Requirement: 同一工具调用聚合为单一轨迹节点

`AgentTraceAggregator` SHALL 把同一 `callId` 的 `tool.started` 与 `tool.completed` / `tool.failed` 合并为一个 TOOL 节点，状态由 `RUNNING` 收敛为终态，并用已持久化的工具调用补全参数、结果与耗时。

#### Scenario: started 与 completed 合并
- **WHEN** 同一 `callId` 先出现 `tool.started` 再出现 `tool.completed`
- **THEN** 轨迹只产生一个 TOOL 节点，状态为 `SUCCEEDED`，含参数、结果与起止时间

#### Scenario: 仅出现 started
- **WHEN** 某 `callId` 只有 `tool.started`（例如等待审批暂停）
- **THEN** 轨迹产生一个状态为 `RUNNING` 的 TOOL 节点，且不重复

#### Scenario: 重复 started 幂等
- **WHEN** 审批恢复后同一 `callId` 再次上报 `tool.started`
- **THEN** 更新既有节点而不新增节点

#### Scenario: 无 callId 事件保持兼容
- **WHEN** 工具事件 payload 不含 `callId`
- **THEN** 按原有「按工具名 FIFO 配对」逻辑聚合，行为不变

#### Scenario: 工具信息不丢失
- **WHEN** 某工具调用已持久化但没有任何工具事件
- **THEN** 轨迹末尾补一个由持久化调用生成的 TOOL 节点（含参数与结果）

### Requirement: 会话面板内联展示执行过程

会话面板 SHALL 在 Agent 消息气泡流中内联展示该 Run 的执行过程节点（模型思考、执行思路、计划、步骤、工具调用、审批），进行中 Run 近实时刷新，历史会话完整呈现；最终回答仍由消息正文或流式输出承载。

#### Scenario: 进行中 Run 近实时刷新
- **WHEN** 当前 Run 处于非终态
- **THEN** 面板通过 SSE 事件触发去抖刷新，并以 5 秒轮询兜底，新出现的思考与工具节点近实时可见，Run 进入终态后停止刷新

#### Scenario: 历史会话完整呈现
- **WHEN** 用户打开包含已结束 Run 的会话
- **THEN** 面板展示该轮完整执行过程节点（含工具参数与结果），不依赖实时连接

#### Scenario: 思考与执行思路分段展示
- **WHEN** 轨迹同时包含模型思考（`THINKING`）与执行思路（`REASONING`）
- **THEN** 面板分别以可折叠的「模型思考」「执行思路」段落展示，不合并为同一列表

#### Scenario: 工具卡片内联展示
- **WHEN** 轨迹包含 TOOL 节点
- **THEN** 面板以紧凑卡片展示工具名、状态、耗时，并可展开查看参数与结果

#### Scenario: 未知节点兜底
- **WHEN** 轨迹返回未识别的节点 kind
- **THEN** 面板按通用事件兜底展示其内容，不丢弃节点、不抛出异常

#### Scenario: 回答与终态不重复
- **WHEN** 轨迹包含 `ANSWER` 或 `TERMINAL` 节点
- **THEN** 内联执行过程时间线不重复渲染回答正文与终态信息，回答仍来自消息正文或流式输出

## MODIFIED Requirements

### Requirement: Agent 运行事件目录（`DETAIL` 10.2）

事件目录 SHALL 新增内部上报端点 `POST /internal/v1/agent-runs/{runId}/tool-events`；`tool.started` / `tool.completed` / `tool.failed` 由运行中实时上报，不再由 `complete()` 批量补发。`reasoning.delta` 继续承载计划与执行摘要，`thinking.delta` 承载模型思考内容，二者在 UI 上分段可折叠展示。

## REMOVED Requirements

无。`ReasoningDetails` 组件由新的内联过程渲染替换，属实现细节，不作为独立需求移除。
