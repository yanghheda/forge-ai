# Tasks

- [x] Task 1: 后端新增「会话 Run 列表」只读接口
  - [x] SubTask 1.1: 在 `AgentRunMapper` 增加按会话读取 Run 摘要的 scope 查询（显式 `organization_id` 条件；按 `agent_messages` 关联去重，取该轮 USER 指令与 AGENT 回答，按 Run 创建时间升序）
  - [x] SubTask 1.2: 在 `AgentRunService` 增加 `listConversationRuns`，先校验会话归属，非本人或不存在抛 `ResourceNotFoundException`
  - [x] SubTask 1.3: `ConversationController` 暴露 `GET /api/v1/agent-conversations/{id}/runs`
  - [x] SubTask 1.4: 集成测试：本人会话返回升序 Run 且字段完整、他人会话返回 404、无 Run 会话返回空数组

- [x] Task 2: 后端新增「Run 轨迹详情」聚合接口
  - [x] SubTask 2.1: 新增纯聚合器 `AgentTraceAggregator`（无 Spring 依赖，输入事件/步骤/工具调用/审批，输出有序节点）：连续 `thinking.delta` 合并为 `THINKING`（按来源节点拆分）、连续 `reasoning.delta` 合并为 `REASONING`、连续 `message.delta` 合并为 `ANSWER`、`tool.*` 按工具名 FIFO 匹配已持久化调用、未匹配的已持久化调用补 `TOOL` 节点、未知事件类型兜底 `EVENT`
  - [x] SubTask 2.2: 聚合器单元测试（不依赖容器）：节点顺序、思考与思路分别聚合、来源节点拆分、工具匹配与补充、审批节点、未知事件兜底、终态耗时
  - [x] SubTask 2.3: 只读查询：按 run 分批读取全部事件（避免单次大结果集）、读取工具调用与审批事实，SQL 全部显式携带 `organization_id`
  - [x] SubTask 2.4: 新增 `AgentTraceService` 与 `AgentRunController` 的 `GET /api/v1/agent-runs/{runId}/trace`（scope：organization + Run 发起人；未授权/不存在 404）
  - [x] SubTask 2.5: 集成测试：思考聚合文本、工具参数与结果、审批节点、终态节点、跨用户与跨公司 404

- [x] Task 3: 模型思考内容（CoT）事件链路
  - [x] SubTask 3.1: `qwen.py` 采集非流式与流式响应中的 `reasoning_content`，并通过 Run 级思考 sink 交由 Runtime 消费
  - [x] SubTask 3.2: `runtime.py` 按图节点（计划/选择工具/收尾）聚合思考文本，节点结束或达到 4000 字符分片时上报；单 Run 累计上限 20000 字符，上报失败只告警不改变 Run 结果
  - [x] SubTask 3.3: `tool_transport.py` 增加 `publish_thinking`，POST 内部端点 `/internal/v1/agent-runs/{runId}/thinking-deltas`
  - [x] SubTask 3.4: Server 侧 `InternalAgentEventController` 增加该端点（校验 Run 凭据与 Run 绑定），`AgentRunStore` + `MybatisAgentRunStore` 增加 `appendThinkingDelta`（沿用 `advanceRunningSequence` + `insertEvent`，事件类型 `thinking.delta`）
  - [x] SubTask 3.5: forge-agent 测试：Provider 返回思考时报文分片与上限、Provider 不返回时不产生事件、上报失败不影响 Run 结果；Server 集成测试：事件持久化与序号推进

- [x] Task 4: 前端轨迹模型与 API client
  - [x] SubTask 4.1: `features/agent-run/api/agent-run-api.ts` 增加 `listConversationRuns` 与 `getRunTrace` 的类型与请求函数
  - [x] SubTask 4.2: 新增 `features/agent-run/model/trace-model.ts`：节点 kind 联合类型、按 kind 的分派信息、未知 kind 兜底、节点分组与耗时格式化工具
  - [x] SubTask 4.3: 单元测试：已知 kind 分派、未知 kind 兜底、分组与格式化辅助逻辑

- [x] Task 5: 轨迹工作台页面与会话面板思考
  - [x] SubTask 5.1: 左侧会话列表（复用 `/v1/agent-conversations`，`?conversationId=` 保持选中，默认最近会话，空态与错误态）
  - [x] SubTask 5.2: 右侧轨迹：按轮次渲染（用户指令、Skill、状态、耗时）+ 节点渲染（`THINKING`/`REASONING` 折叠文本、`TOOL` 参数与结果 JSON、`APPROVAL` 决策信息、`TERMINAL` 终态、未知 kind 兜底）
  - [x] SubTask 5.3: 进行中 Run 的实时刷新（复用 `agentEventUrl` 的 SSE 事件触发即时失效 + 5 秒轮询兜底，终态后停止）
  - [x] SubTask 5.4: `use-conversation-stream` 增加 `thinking.delta` 消费，与会话面板现有执行思路合并展示
  - [x] SubTask 5.5: 组件测试：会话列表渲染与切换、空态、轮次展开触发轨迹请求、进行中 Run 实时刷新、会话面板思考增量展示

- [x] Task 6: 路由、导航、旧实现清理与文档同步
  - [x] SubTask 6.1: 路由改为 `/agent/trace` 并移除 `/agent/trace/:id`；更新 `AgentTraceView` 与 `app-shell.tsx` 菜单/面包屑，去掉 `demo-run`
  - [x] SubTask 6.2: 删除 `features/agent-run/components/agent-run-page.tsx`、`model/run-reducer.ts` 与其测试，收敛 barrel 导出（保留会话面板仍在用的快照/审批/SSE 能力）
  - [x] SubTask 6.3: 同步 `docs/design/ForgeAI_详细设计_v1.0.md` 10.2 事件目录登记 `thinking.delta`，并修订 4.8/10.2 中「不保存/不展示模型私有推理」的对应表述
  - [x] SubTask 6.4: 前端 `lint`、`lint:boundaries`、`format:check` 通过，且无残留引用

- [x] Task 7: 全量质量门禁
  - [x] SubTask 7.1: 运行 `make ci`（format-check、lint、contracts-check、test、build）与 `git diff --check`，修复全部失败项

# Task Dependencies

- Task 5 依赖 Task 4（页面使用轨迹模型与 API client）；SubTask 5.4 只依赖 Task 3 的事件类型约定
- Task 6 依赖 Task 5（旧页面在新页面可用后再删除）
- Task 7 依赖 Task 1–6 全部完成
- Task 1、Task 2、Task 3、Task 4 之间无依赖，可并行实施（接口与事件契约已在 spec.md 固定）
