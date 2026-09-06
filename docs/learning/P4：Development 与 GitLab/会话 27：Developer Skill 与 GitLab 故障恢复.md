# 会话 27：Developer Skill 与 GitLab 故障恢复

## 我完成了什么

- 新增版本化 `developer` Skill、`developer-context-v1` 最小上下文模板，以及 Tech Design、Dev Task、启动开发、读取 CI 日志四个 Tool Contract。
- Developer 白名单只包含读取交付上下文、创建技术设计/Dev Task、启动 Branch/MR 和读取 CI 日志；不包含 QA 写入、merge、触发 Pipeline 或部署。
- `AgentSkill.DEVELOPER` 复用既有 Agent Run、MEDIUM 确认、审批恢复与内部 Tool 防线。
- Agent 创建 Tech Design 与 Dev Task 复用人工应用服务；启动开发复用 `DevelopmentService`，未引入 Agent 专属业务规则。
- CI Job 日志在返回 UI 或 Agent 前执行字节上限和凭据脱敏，并显式返回 `truncated`、`redacted`。
- 新增 V21 恢复字段和定时 reconciliation job：扫描陈旧 `PROCESSING` 操作，以原始幂等键和冻结目标分支恢复；瞬时错误指数退避，冲突与非瞬时错误转人工处理。
- 新增七个 JSONL Eval Case，覆盖正向生成、确认创建、429、timeout、已有 Branch/MR、失败 Pipeline 和跨 Project 检索。

## 核心设计

### 调用链

Developer Run：

`AgentRunController → AgentRunService → Context Manifest(DEVELOPER) → forge-agent LangGraph → InternalToolController → AgentToolExecuteService`

本地生成：

`create_tech_design/create_dev_task → DocumentService/DevelopmentService → MyBatis Store → MySQL`

GitLab 启动与恢复：

`start_development → 本地 PROCESSING 意图 → GitLab Branch/MR 查询或创建 → 本地 Branch/MR 快照 + 状态推进 → Tool 幂等结果`

`DevelopmentReconciliationScheduler → 陈旧 PROCESSING → 同一 DevelopmentService 编排 → 完成 / 退避 / MANUAL_ACTION_REQUIRED`

CI 解释输入：

`get_pipeline_log → PipelineService → GitLab Job trace → 尾部限制 → Secret 脱敏 → 结构化 Tool Result`

### 最小上下文与工具边界

- Manifest 只携带 Workspace、Project、可选 Work Item scope 和版本化资源引用，不复制 PRD/UX 正文。
- Developer 需要正文时只能通过带当前用户权限与项目过滤的读取/检索 Tool 获取。
- Prompt 规则要求区分 Tool 事实与模型建议；GitLab 结果未知时只报告 `PROCESSING`，不能猜测成功或失败。
- Developer 不改代码、不 merge、不写 QA、不部署；这些能力没有进入 Skill 白名单。

### 数据与事务边界

- MySQL 是 Run、Tool Call、开发操作意图、Branch/MR/Pipeline 快照的事实来源。
- `start_development` 的 GitLab HTTP 调用位于数据库事务之外。调用前短事务写 `PROCESSING`，调用后短事务保存快照并完成操作。
- Agent Tool 幂等记录不能与远端写形成单个 ACID 事务；崩溃空窗依靠远端查询、同一幂等键和后台 reconcile 收敛。
- Reconcile 冻结 `target_branch`，避免恢复时仓库默认分支变化导致请求语义漂移。
- Worker 使用 `FOR UPDATE SKIP LOCKED` 领取单条操作并写短租约，多实例不会同时恢复同一条意图；Worker 崩溃后租约到期可再次领取。
- 429、timeout、unavailable 使用有上限的指数退避；远端同名分支不同 SHA、认证/权限等非瞬时错误停止自动重试，进入人工处理。
- 日志不落新的业务事实，只返回受限、脱敏的瞬时读取结果。

## 逐文件关键代码

- `packages/forge-contracts/skills/developer.yaml`：Developer Tool 白名单、Prompt 与上下文模板版本。
- `packages/forge-contracts/context-templates/developer-context-v1.yaml`：最小资源引用、事实/建议分离和禁止能力。
- `packages/forge-contracts/tools/*.yaml`：四个 Developer Tool 的 Schema、权限、风险、幂等、超时和敏感字段。
- `forge-agent/src/forge_agent/gateway/runtime.py`：Manifest 接受 `DEVELOPER`，仍受有效期、Tool 白名单和调用预算约束。
- `forge-agent/src/forge_agent/contracts/registry.py`：加载 Prompt/context template 版本，供运行时和回归识别配置变化。
- `forge-server/.../AgentSkill.java`：Developer Run 的启动权限为 `task.create`。
- `forge-server/.../AgentToolExecuteService.java`：Tool 到人工应用服务的映射；GitLab 外部写使用无外层事务的可恢复路径。
- `forge-server/.../PipelineService.java`：日志尾部上限与 GitLab Token/Bearer/PAT 脱敏。
- `V21__source_control_reconciliation.sql`：冻结目标分支、恢复次数、下次尝试和安全错误码。
- `DevelopmentService`、`DevelopmentStore` 与 MyBatis 实现：统一人工/Agent/reconcile 编排和瞬时/人工恢复分类。
- `DevelopmentReconciliationScheduler.java`：单 tick 最多处理 20 条，避免故障时占满调度线程。
- `tests/evals/developer.jsonl`：会话验收场景；确定性 Gate 校验 Tool、审批与输出契约，不用随机语言差异阻塞 CI。

## 验证证据

- Tool/Skill Schema 校验通过。
- forge-agent 契约、Eval、Runtime、Transport：28 passed。
- `DevelopmentServiceTest`：覆盖 timeout reconcile、429 保留意图、已有 Branch/MR、SHA 冲突及后台退避。
- `PipelineServiceTest`：覆盖日志尾部上限和 PRIVATE-TOKEN、Bearer、`glpat-` 脱敏。
- `ToolContractRegistryTest`：覆盖 Developer 精确白名单与禁用 Tool。
- Server 纯单元目标均通过；Testcontainers 集成测试在当前沙箱因 Docker socket 无权限未能启动，未吞掉该失败。
- 按本轮约定未执行 `make ci`。

## 风险与遗留问题

- 多实例 scheduler 当前依赖远端幂等查询收敛，没有数据库 claim/lease；可能产生重复查询，但不会重复创建同基准 Branch 或 open MR。若并发量上升，应增加 `SKIP LOCKED` claim。
- V21 以前遗留的 `PROCESSING` 行没有冻结目标分支，迁移默认空串，恢复时会使用当前默认分支；新请求不存在该问题。
- 日志脱敏是防御性模式匹配，不可能识别所有业务自定义 Secret；GitLab CI 仍应启用 masked variables，后续安全会话可扩展 Workspace 级敏感词。
- Eval 当前只做确定性 Gate；模型语言 rubric 的评分与版本对比存档仍需接入真实模型评测流水线。
- 本轮没有实现 QA/Test Case、merge、代码修改或部署，保留给后续会话。

## 复盘问题

1. 为什么 Agent Tool 的幂等记录不能解决 GitLab 已成功但本地超时的“不确定结果”，reconcile 还必须先查远端？
2. 为什么 `start_development` 的远端请求必须移出数据库事务，同时又要在请求前持久化 `PROCESSING` 意图？
3. 为什么失败 CI 的解释必须携带 `truncated/redacted` 元数据，并把日志事实与修复建议分开？
