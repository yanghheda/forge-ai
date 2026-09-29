# 执行轨迹页面重构（会话级轨迹工作台 + 模型思考内容）Spec

## Why

当前 `/agent/trace/:id` 只支持按 Run 深链打开单次运行，页面仅渲染步骤时间线与连接状态；菜单里硬编码的 `/agent/trace/demo-run` 根本不存在，打开即报「运行不存在」。执行过程里真正有价值的信息——模型思考了什么、调用了哪些工具、参数与结果、审批由谁做了决策、最终回答与耗时——全部看不到。

同时，现有「思考」只来自计划项摘要（`reasoning.delta`），无法呈现模型真实的推理过程。本次一并引入模型思考内容（Chain-of-Thought）：forge-agent 采集 Provider 返回的 `reasoning_content`，以独立的 `thinking.delta` 事件上报并持久化，使轨迹页与会话面板都能像 ChatGPT / Trae 那样展示模型的思考过程。

## What Changes

- 执行轨迹页改为「会话级轨迹工作台」：左侧为当前登录成员自己的会话列表，右侧为所选会话的完整执行轨迹（该会话多轮 Run 依时间排列）。
- 轨迹节点模型化：模型思考（THINKING）/ 执行思路（REASONING）/ 计划（PLAN）/ 步骤（STEP）/ 工具调用（TOOL）/ 审批决策（APPROVAL）/ 回答（ANSWER）/ 终态（TERMINAL）/ 兜底事件（EVENT），未知事件类型不丢弃。
- **引入模型思考内容（CoT）链路**：新增 `thinking.delta` 事件类型，由 forge-agent 在 Provider 返回 `reasoning_content` 时按节点聚合上报，Server 持久化到 `agent_events`，轨迹页以此渲染 THINKING 节点；Provider 不支持思考时自动退化为现状（无 THINKING 节点）。
- 新增两个只读聚合接口：会话 Run 列表、Run 轨迹详情。
- 轨迹页为全员可用的开发自助页面，不引入 RBAC 权限码；数据范围收紧为「本人」：会话列表复用本人会话接口，Run 列表要求会话归属本人，轨迹详情要求 Run 发起人为本人（organization + user 双层 scope）。
- 路由与导航调整：菜单「执行轨迹」指向 `/agent/trace`；删除 `/agent/trace/:id` 深链与硬编码 `demo-run`。**BREAKING**
- 删除旧单 Run 页面与 `run-reducer`；轨迹页内嵌审批操作（批准/拒绝按钮）移除，审批仍在 Agent 指令中心会话面板处理。
- 会话面板同步消费 `thinking.delta`，与现有执行思路合并展示，实时看到模型思考。
- 为后续增强预留扩展位：后端用显式的「事件类型 → 节点 kind」映射表聚合，前端按 kind 分派渲染且未知 kind / 未识别事件走通用渲染。
- 明确不做：不实现多轮对话历史的思考重放还原（仅按持久化事件展示）；不把思考内容写入 `agent_messages.body`、不进入 RAG 索引。

## Impact

- Affected specs：Agent Run Trace 读取能力（`DETAIL` 4.8、10.2 事件目录需登记 `thinking.delta`）；forge-web 页面与路由（`DETAIL` 13）。不改变事实来源、认证、事务、Agent Tool 安全边界与部署拓扑，无需新增 ADR。
- Affected code：
  - forge-agent：`gateway/qwen.py`（采集 `reasoning_content`）、`gateway/runtime.py`（按节点聚合并上报思考）、`gateway/tool_transport.py`（`ServerAnswerStream.publish_thinking`）
  - forge-server：`agent/controller/InternalAgentEventController.java`（新增思考增量内部端点）、`agent/application/AgentRunStore.java`、`agent/infrastructure/persistence/MybatisAgentRunStore.java`、`agent/controller/AgentRunController.java`、`agent/application/AgentRunService.java`、新增 `agent/application/AgentTraceService.java`、新增 `agent/application/AgentTraceAggregator.java`（纯聚合器）、`agent/infrastructure/persistence/AgentRunMapper.java`、`AgentToolCallMapper.java`、`ApprovalMapper.java`、`conversation/controller/ConversationController.java`、`conversation/application/ConversationService.java`
  - forge-web：`src/features/agent-run/**`（api / model / components / hooks）、`src/features/console/hooks/use-conversation-stream.ts`、`src/views/agent-trace-view.tsx`、`src/routing/app-router.tsx`、`src/components/app-shell/app-shell.tsx`
  - 文档：`docs/design/ForgeAI_详细设计_v1.0.md` 10.2 事件目录登记 `thinking.delta`，并修订 4.8/10.2 中「不保存/不展示模型私有推理」的对应表述

## ADDED Requirements

### Requirement: 会话级执行轨迹工作台

系统 SHALL 在 `/agent/trace` 提供轨迹工作台：左侧为当前登录成员自己的会话列表，右侧为所选会话的完整执行轨迹。

#### Scenario: 打开页面默认选中最近会话

- **WHEN** 已登录成员打开 `/agent/trace`
- **THEN** 左侧按最近更新时间倒序展示本人会话（标题、绑定需求、更新时间）
- **AND** 自动选中最近更新的会话并在右侧加载其轨迹

#### Scenario: 通过 URL 保持选中会话

- **WHEN** 成员切换会话
- **THEN** 选中会话 ID 写入 URL 查询参数 `conversationId`，刷新或分享链接后仍选中同一会话
- **AND** 参数指向不存在或非本人会话时回退到默认选中最近会话

#### Scenario: 空态

- **WHEN** 成员没有任何会话，或所选会话尚未产生 Run
- **THEN** 左侧或右侧展示明确的空态提示，不出现报错

#### Scenario: 右侧按轮次展示会话轨迹

- **WHEN** 所选会话已有关联 Run
- **THEN** 右侧按 Run 创建时间升序展示每一轮：该轮用户指令、Skill 徽标、Run 状态、开始时间与耗时
- **AND** 每一轮内按轨迹节点顺序展示模型思考、执行思路、步骤、工具调用、审批、回答与终态
- **AND** 最新一轮默认展开，历史轮次折叠并可按需展开后加载其轨迹

#### Scenario: 进行中的 Run 实时刷新

- **WHEN** 会话内存在非终态 Run
- **THEN** 页面通过该 Run 的 SSE 事件到达即时刷新其轨迹，并以 5 秒轮询兜底
- **AND** Run 进入终态后停止实时刷新

### Requirement: 模型思考内容（CoT）事件链路

系统 SHALL 采集模型思考内容并以 `thinking.delta` 事件持久化，供轨迹页与会话面板展示。

- forge-agent SHALL 在 Provider 响应中读取 `reasoning_content`（非流式与流式两种调用路径）。
- 思考文本 SHALL 按图节点（计划 / 选择工具 / 收尾）聚合后上报，单条 `delta` 不超过 4000 字符，单 Run 累计思考文本上限 20000 字符，超出截断并保留已上报内容。
- 上报 SHALL 复用 Run 级凭据的内部端点，Server 校验 Run 与公司绑定后以 `thinking.delta` 事件追加写入 `agent_events`，并推进 `last_sequence`。
- 思考内容 SHALL 不写入 `agent_messages.body`，不进入 RAG 索引，不改变 Run 的业务终态判定。

#### Scenario: Provider 返回思考内容

- **WHEN** 模型响应包含 `reasoning_content`
- **THEN** 该节点结束后以 `thinking.delta` 事件上报，payload 含 `delta` 与来源节点标识
- **AND** 事件可通过既有 SSE 重放被前端订阅

#### Scenario: Provider 不返回思考内容

- **WHEN** 所用模型不支持思考输出
- **THEN** 不上报任何 `thinking.delta`，Run 与页面行为退化为现状（仍展示执行思路与计划）

#### Scenario: 思考上报失败不影响业务结果

- **WHEN** 思考增量上报因网络或状态原因失败
- **THEN** Run 继续执行，仅在日志记录告警，不因展示类事件失败而改变 Run 终态

### Requirement: 会话 Run 列表只读接口

系统 SHALL 提供 `GET /api/v1/agent-conversations/{id}/runs`，返回该会话可见的 Run 摘要列表，按 Run 创建时间升序。

响应字段：`runId`、`skill`、`workItemId`、`status`、`terminal`、`startedAt`、`finishedAt`、`errorCode`、`lastSequence`、`triggerMessage`（该轮用户指令正文）、`answer`（该轮 Agent 可见回答，未产生时为空）。

#### Scenario: 返回本人会话的 Run

- **WHEN** 成员读取自己的会话
- **THEN** 返回该会话全部 Run，不含其他会话或其他成员的 Run

#### Scenario: 非本人会话拒绝

- **WHEN** 成员读取其他成员的会话 ID，或该会话不存在
- **THEN** 返回 404，且不泄露会话是否存在

#### Scenario: 无 Run 的会话

- **WHEN** 会话尚无任何 Run
- **THEN** 返回空数组

#### Scenario: 授权边界

- **WHEN** 任何实现该接口的 SQL 执行
- **THEN** 语句显式携带 `organization_id` 条件，并同时校验会话归属 `user_id`

### Requirement: Run 轨迹详情只读接口

系统 SHALL 提供 `GET /api/v1/agent-runs/{runId}/trace`，返回按事件序号有序的轨迹节点，用于轨迹工作台展示；未结束的 Run 也返回当前已持久化节点。

响应字段：`runId`、`skill`、`workItemId`、`status`、`terminal`、`startedAt`、`finishedAt`、`errorCode`、`lastSequence`、`nodes`。

节点字段：`key`（稳定键）、`kind`、`title`、`status`、`sequence`、`startedAt`、`finishedAt`、`detail`（按 kind 变化的键值对象）。

节点 kind 与 detail：

| kind        | 数据来源                                               | detail 关键字段                                                                                               |
| ----------- | ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------- |
| `THINKING`  | 连续的 `thinking.delta` 事件合并                       | `text`、`node`（来源图节点）                                                                                  |
| `REASONING` | 连续的 `reasoning.delta` 事件合并                      | `text`                                                                                                        |
| `PLAN`      | `plan.created` 事件                                    | `steps[]`                                                                                                     |
| `STEP`      | `step.*` 事件 + `agent_steps` 时间与摘要               | `stepNo`、`type`、`name`、`status`、`summary`、`errorCode`、耗时                                              |
| `TOOL`      | `tool.*` 事件 + `agent_tool_calls` 已持久化事实        | `toolName`、`toolVersion`、`riskLevel`、`status`、`summary`、`errorCode`、`arguments`、`result`、`approvalId` |
| `APPROVAL`  | `approval.*` 事件 + `approvals` 事实                   | `approvalId`、`toolName`、`riskLevel`、`status`、`reason`、`decidedBy`、`expiresAt`                           |
| `ANSWER`    | 连续的 `message.delta` 合并 + `message.completed`      | `text`                                                                                                        |
| `TERMINAL`  | `agent.completed` / `agent.failed` / `agent.cancelled` | `status`、`summary`、`errorCode`、`durationMs`                                                                |
| `EVENT`     | 其余任何事件类型的兜底                                 | `eventType`、`payload`                                                                                        |

#### Scenario: 模型思考聚合

- **WHEN** 某 Run 持久化了多条连续的 `thinking.delta`
- **THEN** 轨迹返回 `THINKING` 节点，`detail.text` 为按序拼接的完整思考文本，`sequence` 为第一条序号
- **AND** 来源节点不同或序列不连续时拆分为多个 `THINKING` 节点，并在标题上区分来源节点

#### Scenario: 执行思路聚合

- **WHEN** 某 Run 持久化了多条连续的 `reasoning.delta`
- **THEN** 轨迹返回 `REASONING` 节点，`detail.text` 为按序拼接的完整文本

#### Scenario: 工具调用携带结构化详情

- **WHEN** 某轮执行了已持久化到 `agent_tool_calls` 的工具调用
- **THEN** 对应 `TOOL` 节点包含工具名、版本、风险等级、状态、结构化 `arguments` 与 `result`
- **AND** 未持久化的只读工具（如 LOW 风险查询）返回事件已有的摘要字段，不报错

#### Scenario: 已持久化但无对应事件的工具调用

- **WHEN** 存在已持久化的 Tool Call 但事件流中没有对应 `tool.*` 事件
- **THEN** 该调用仍以 `TOOL` 节点出现在轨迹中，保证参数与结果可见

#### Scenario: 审批决策

- **WHEN** Run 发生过审批（等待、批准、拒绝、过期、取消）
- **THEN** 返回 `APPROVAL` 节点，含审批状态、风险等级、原因与决策人（可用时）

#### Scenario: 未知事件类型不丢弃

- **WHEN** 事件流中出现映射表未覆盖的事件类型
- **THEN** 该事件以 `EVENT` 节点返回，携带事件类型与原始 payload 摘要，页面不崩溃、不静默丢弃

#### Scenario: 终态信息

- **WHEN** Run 已进入终态
- **THEN** 轨迹包含 `TERMINAL` 节点，含最终状态、摘要、错误码与总耗时

#### Scenario: 访问边界

- **WHEN** 请求者不是该 Run 的发起人，或 Run 不存在、不属于请求者的公司
- **THEN** 返回 404
- **AND** 所有相关 SQL 显式携带 `organization_id` 条件

### Requirement: 轨迹节点渲染与扩展位

前端 SHALL 按节点 `kind` 分派渲染，未知 `kind` 走通用渲染（事件类型 + 折叠 JSON），保证后端新增节点类型时页面无需重构。

#### Scenario: 思考类节点展示深度

- **WHEN** 渲染 `THINKING` 节点
- **THEN** 标题标注「模型思考」与来源节点，默认折叠但展示前两行预览，展开后展示完整文本
- **WHEN** 渲染 `REASONING` 节点
- **THEN** 以相同折叠文本组件渲染，标题标注「执行思路」

#### Scenario: 工具节点展示深度

- **WHEN** 渲染 `TOOL` 节点
- **THEN** 展示工具名、风险等级、状态与耗时摘要行，展开后以 JSON 展示参数与结果

#### Scenario: 未知 kind 兜底

- **WHEN** 收到 `kind` 不在前端已知集合内的节点
- **THEN** 以通用节点渲染其标题、状态与 detail JSON，不影响其他节点展示

### Requirement: 轨迹页访问边界

轨迹页 SHALL 不设置 RBAC 权限码，任意已登录成员均可访问并只看到自己的会话与轨迹；接口侧以 organization + user 双层 scope 兜底。

#### Scenario: 普通成员可用

- **WHEN** 不具备 `agent.run` 等权限码的成员打开 `/agent/trace`
- **THEN** 页面正常展示其本人会话与轨迹，不出现权限拦截

## MODIFIED Requirements

### Requirement: 导航与路由入口

执行轨迹入口由「硬编码 `demo-run` 的单 Run 页面」改为「无参数的工作台页面」。

- `app-shell.tsx`：菜单「执行轨迹」指向 `/agent/trace`，面包屑仍显示「执行轨迹」。
- `app-router.tsx`：新增 `/agent/trace` 路由指向 `AgentTraceView`；移除 `/agent/trace/:id`。

### Requirement: 会话面板思考展示

会话面板（`AgentConversationPanel` + `use-conversation-stream`）SHALL 同时消费 `thinking.delta` 与 `reasoning.delta`，按到达顺序合并展示，思考进行中显示进行态、完成后保留可展开内容。

#### Scenario: 会话内实时思考

- **WHEN** 当前会话的 Run 正在上报模型思考
- **THEN** 会话面板实时追加思考文本，不需要刷新页面

## REMOVED Requirements

### Requirement: 单 Run 轨迹页与页内审批操作

**Reason**：轨迹页定位为只读浏览工作台；会话级工作台已覆盖单 Run 的完整信息，页内审批操作与 Agent 指令中心会话面板重复。

**Migration**：单 Run 的历史信息通过所选会话的对应轮次查看；审批操作继续在 Agent 指令中心会话面板（`ConversationApproval`）完成。

**同时删除**：`features/agent-run/components/agent-run-page.tsx`、`features/agent-run/model/run-reducer.ts` 及其测试（删除后无其他消费方）。
