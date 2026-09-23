# ADR-023：Agent 动作覆盖范围与强制人工确认

- 状态：Accepted
- 日期：2026-09-23
- 依据：本次用户确认的「除领导审批外，其余动作都能由 Agent 对话完成」目标。
- 相关：收紧 ADR-017 第 5 条中四领域会签票的既有越界；扩展 ADR-018 的确认模型，新增与 Run 策略无关的强制人工确认。

## 上下文

需求流转按 ADR-022 改版为 15 个需求状态，并新增了需求列表、任务看板、缺陷列表。但 Agent 侧仍停留在旧流程：

- 会话只能绑定 Requirement，且 `StageSkillRouter` 只覆盖 15 个状态中的 8 个，其余阶段无法开启对话。
- 人员配置、任务执行人动作、缺陷修复与验证、测试执行、需求与任务编辑都没有对应 Tool。
- 缺少成员、任务、缺陷等读取 Tool，模型只能凭用户原话猜测 `assigneeUserId`。
- `AgentStageActionPolicy` 对非 Release 的所有 Skill 放行了 `APPROVE_PRODUCT_REVIEW` / `REJECT_PRODUCT_REVIEW`，与 ADR-017「Product/UX Review 保持人工边界」冲突。

## 决策

1. **除领导审批外，业务动作全部 Tool 化。** 保持人工、不开放给 Agent 的动作固定为：四领域会签票（`APPROVE_PRODUCT_REVIEW` / `REJECT_PRODUCT_REVIEW`）、产品负责人需求审核与驳回、UX 评审通过与驳回、验收、任务负责人审核（`APPROVE` / `REJECT`）、HIGH 风险部署审批。其余动作通过新增 Tool 开放。

2. **收紧会签票边界。** 从 `AgentStageActionPolicy` 与 `advance_requirement` 的动作枚举中移除四领域会签票，撤销 ADR-017 落地时对该边界的事实性放宽。四票仍由 `DeliveryWorkflowPolicy.recordReview` 在人工入口累积。

3. **新增 `requires_confirmation` 契约字段。** Tool 契约可声明该工具必须人工确认；命中时 `AgentToolExecuteService` 在 Run 的 `mediumToolConfirmation` 策略之前进入审批分支，因此 `ALLOW` 与 `ASK` 都停在 `WAITING_APPROVAL`。用于 `record_test_result`、`complete_test_run`（质量事实）、`assign_participants`（授权图）与 `delete_requirement`（不可逆删除）。字段缺省为 `false`，既有契约行为不变。

4. **会话与 Run 绑定根需求。** 会话上下文允许是需求、任务或缺陷；服务端统一上溯到根 Requirement，Skill 由根需求当前状态决定。任务与缺陷动作 Tool 必须校验目标工作项属于 Run 绑定的根需求。

5. **任务与缺陷动作按 Skill 收敛。** `AgentStageActionPolicy` 增加任务级与缺陷级动作白名单，任务审核动作永不入白名单，且工具内校验任务类型与 Skill 匹配，避免 Skill 成为横向扩权通道。

6. **不可逆动作必须声明强制确认。** 需求软删除不可逆且无幂等恢复路径，因此 `delete_requirement` 契约声明 `requires_confirmation: true`：Agent 可以发起，但落库前必然停在 `WAITING_APPROVAL` 等人工确认；仅草稿需求允许删除，非草稿由应用服务以 `INVALID_TRANSITION` 拒绝。

## 后果

- 用户可以在任意阶段的页面上用自然语言完成交付动作，仅会签、审核、验收与部署需要人工入口。
- 新增动作仍复用人工入口相同的应用服务、权限、乐观锁、幂等与 Guard，Agent 不成为旁路授权通道。
- 写入质量事实与授权图的动作需要二次人工确认，Run 会暂停在 `WAITING_APPROVAL`，前端必须能展示并处理该状态。
- 每新增一个 Tool 都必须同步契约 YAML、Skill allowlist、服务端分发与两侧注册表测试。

## 替代方案

- 保留四领域会签票给 Agent：拒绝，因为会签是领域领导的责任边界，Agent 代投会消灭评审事实。
- 让 Agent 直接写测试结论而不确认：拒绝，违反 ADR-017 对 QA 结论的确定性要求。
- 为任务与缺陷页面各自实现独立聊天后端：拒绝，会产生不同的权限与 Trace 语义。
