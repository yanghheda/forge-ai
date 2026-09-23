# Agent 能力优化实施计划

## 一、背景与目标

需求流转已按 ADR-022 改版为 15 个需求状态（四领域负责人 + 执行人、五态任务、缺陷回流），并新增了**需求列表**、**任务看板**、**缺陷列表**三个页面。但 Agent 侧仍停留在旧流程的 20 个 Tool 上，导致：

* **多数阶段根本开不了对话**：`StageSkillRouter.route` 只覆盖 15 个状态中的 8 个，其余直接抛 `IllegalArgumentException("work item stage does not support agent conversation")`。

* **写动作大面积缺失**：人员配置、任务执行人动作（开始/提交待验证/阻塞/恢复）、缺陷修复与验证、测试执行、任务与需求编辑都没有 Tool。

* **读动作缺失导致模型瞎猜**：没有成员列表，`create_ux_task` 的 `assigneeUserId` 只能靠正则从用户原话里抠。

目标：**除“领导审批”外，其余业务动作都能在 Agent 对话里完成**，同时让 Agent 的使用体验更好（阶段感知提示词、缺失材料可解释、页面级上下文入口）。

## 二、边界裁定（已与用户确认）

**保持人工、不开放给 Agent**：

| 类别      | 动作                                                 |
| ------- | -------------------------------------------------- |
| 四领域会签   | `APPROVE_PRODUCT_REVIEW` / `REJECT_PRODUCT_REVIEW` |
| 产品负责人审核 | `APPROVE_REQUIREMENT` / `REJECT_REQUIREMENT`       |
| UX 评审   | `APPROVE_UX_REVIEW` / `REJECT_UX_REVIEW`           |
| 验收      | `ACCEPT_REQUIREMENT`                               |
| 任务负责人审核 | `APPROVE` / `REJECT`（任务待验证→已完成）                    |
| 部署      | `deploy_release`（HIGH，保持现有二人审批）                    |

> **注意**：当前 `AgentStageActionPolicy.allows` 对非 RELEASE 的所有 Skill 都放行了 `APPROVE_PRODUCT_REVIEW` / `REJECT_PRODUCT_REVIEW`（`AgentStageActionPolicy.java:12-13`），与 ADR-017 §5「Product/UX Review 保持人工边界」冲突。本次一并收紧，属 Agent Tool 安全边界变更，**先补 ADR-023**。

**QA 测试执行**：允许 Agent 执行，但 `record_test_result` 与 `complete_test_run` **无论 Run 策略是 ALLOW 还是 ASK 都强制人工确认**后再落库（守住 ADR-017「QA 结论不能由模型决定」）。

**明确不做**：需求软删除（不可逆、无幂等恢复路径，保留人工入口）。

## 三、现状差距（检查结论）

1. **阶段路由**：`StageSkillRouter` 缺 `REQUIREMENT_APPROVAL`、`READY_FOR_REVIEW`、`READY_FOR_UX`、`IN_FIX_VERIFICATION`、`IN_ACCEPTANCE`。
2. **动作策略**：`AgentStageActionPolicy` 只有需求级动作，无任务级 / 缺陷级。
3. **会话绑定**：`ConversationService.send` 只接受 REQUIREMENT，任务/缺陷页面无法开对话。
4. **Tool 覆盖**：见下表（❌ 为本次补齐）。

| 流程动作                          | 当前 Tool                 |
| ----------------------------- | ----------------------- |
| 创建/编辑需求、配人员                   | 仅 create\_requirement ❌ |
| 需求列表 / 成员 / 任务 / 缺陷 / 测试运行查询  | ❌                       |
| 任务：开始、提交待验证、阻塞、恢复、编辑          | ❌                       |
| 缺陷：开始修复、解决、验证、重开、关闭、阻塞、编辑     | 仅 create\_bug ❌         |
| 测试：建运行、记录结果、完成运行              | ❌                       |
| 其余（PRD/UX/技术设计/开发任务/分支 MR/发布） | ✅ 已具备                   |

1. **体验**：Skill 契约的 `prompt_version` / `context_template` 已下发 manifest 但从未使用；`get_work_item` 只在需求类型下返回 `availableActions` / `guardHints`；`_complete_action` 只硬编码 3 个工具。

## 四、实施步骤

### Phase 0 — ADR 与文档

* 新增 `docs/adr/ADR-023-agent-action-coverage-and-forced-confirmation.md`：记录 ① 除领导审批外的动作全部 Tool 化；② 四领域会签票从 Agent 白名单移除（收紧既有越界）；③ 新增 `requires_confirmation` 契约字段与 QA 结论的人工确认边界；④ 会话可绑定任务/缺陷并上溯根需求。

* 更新 `docs/adr/README.md`；在 `docs/context/INDEX.md` 的「Tool、风险、审批、暂停与恢复」与「Agent Run、Deep Agents、Checkpoint、Skill」主题行补 ADR-023。

### Phase 1 — 阶段路由与会话绑定

* `conversation/application/StageSkillRouter.java`：补全 15 态（枚举 switch，**不写 default**，编译器保证穷尽）：

  | 状态                                                                                    | Skill     |
  | ------------------------------------------------------------------------------------- | --------- |
  | DRAFT / REQUIREMENT\_APPROVAL / READY\_FOR\_REVIEW / PRODUCT\_REVIEW / IN\_ACCEPTANCE | PRODUCT   |
  | READY\_FOR\_UX / UX\_IN\_PROGRESS / UX\_REVIEW                                        | UX        |
  | READY\_FOR\_DEV / IN\_DEVELOPMENT                                                     | DEVELOPER |
  | READY\_FOR\_QA / IN\_QA / IN\_FIX\_VERIFICATION                                       | QA        |
  | READY\_FOR\_RELEASE / RELEASED                                                        | RELEASE   |

  任务/缺陷类状态显式 `case -> throw`（路由只吃根需求状态）。

* `conversation/application/ConversationService.send`：把「必须是 REQUIREMENT」改为「上溯到根需求」。非需求时用 `DeliveryWorkflowStore.requirementId(org, itemId)` 上溯，为空则拒绝；会话与 Run 都绑定 root，skill 由 root 状态决定（符合 ADR-017 §2）。

* 同步更新 `StageSkillRouterTest`、`ConversationServiceTest` 中旧断言。

### Phase 2 — 收紧审批边界

* `agent/application/AgentStageActionPolicy.java`：从 PRODUCT/UX/DEVELOPER/QA 移除 `APPROVE_PRODUCT_REVIEW` / `REJECT_PRODUCT_REVIEW`（删掉 `AgentStageActionPolicy.java:12-13` 的提前返回）。

* `packages/forge-contracts/tools/advance_requirement.yaml`：enum 移除这两个 action。

* 同步 `AgentStageActionPolicyTest`、`forge-agent/src/forge_agent/gateway/runtime.py::FakeLanguageModel._transition_for`（含 `PRODUCT_REVIEW` 的 4 条映射）。

### Phase 3 — 读工具（LOW，5 个）

`list_requirements`（`OrganizationRequirementService.list`，支持 mine/q/status/分页）、`list_tasks`（`WorkItemCommandService.tasks` 或 `ConsoleService.board` 按执行人/泳道筛选）、`list_bugs`（`BugService.list`）、`list_members`（`OrganizationRequirementService.members`）、`get_test_run`（`QaService.latestRun` / `run`）。

* 同时扩展 `AgentToolExecuteService.workItem(...)`：**对所有类型**返回 `availableActions` / `guardHints`（`RequirementTransitionService.hints` 本就支持任意 WorkItem），并补 `parentRequirementId`（`DeliveryWorkflowStore.requirementId`）。

* 说明：用 `get_test_run` 而非 `list_test_cases`，因为 `record_test_result` / `complete_test_run` 需要 runId、resultId 与各自的 `expectedVersion`，用例节点已在 `get_delivery_graph` 可见。

### Phase 4 — 写工具（MEDIUM，7 个）

| Tool                  | 后端服务                                                 | 说明                                                                             |
| --------------------- | ---------------------------------------------------- | ------------------------------------------------------------------------------ |
| `update_requirement`  | `WorkItemCommandService.update`                      | 标题/描述/优先级/负责人                                                                  |
| `update_task`         | 同上                                                   | 任务字段与改派执行人                                                                     |
| `update_bug`          | 同上                                                   | 严重级别与修复人修正                                                                     |
| `assign_executors`    | `OrganizationRequirementService.replaceExecutors`    | 各领域执行人                                                                         |
| `advance_task`        | `RequirementTransitionService.transition`            | 仅 `START / SUBMIT_REVIEW / BLOCK / UNBLOCK`                                    |
| `advance_bug`         | `BugService.transition`                              | `START_FIX / RESOLVE / VERIFY / REOPEN / CLOSE / BLOCK / UNBLOCK`（排除 `CANCEL`） |
| `assign_participants` | `OrganizationRequirementService.replaceParticipants` | **强制人工确认**（改的是授权图）                                                             |

* **不合并** `update_requirement` / `update_task`：服务、权限资源（`requirement.edit` vs `task.edit`）与可写字段集都不同，合并会让 product skill 横向拿到 task 写权限。

* **不合并** `advance_task` / `advance_bug`：`WorkflowAction` 与 `BugAction` 的 `BLOCK` 同名不同义，合并后 enum 歧义。

* `AgentStageActionPolicy` 新增 `allowsTask(skill, WorkflowAction)` 与 `allowsBug(skill, BugAction)`；`APPROVE` / `REJECT` 永不入白名单。

* **工具内必须校验任务类型与 skill 匹配**（UX skill 不得 START 一个 DEV\_TASK），否则 skill 会变成横向扩权通道。

* **幂等键**用 `runId + ":" + toolName + ":" + itemId`，不要沿用 `advance_requirement` 的 `runId + ":" + action.name()`。

### Phase 5 — 强制确认机制与 QA 工具（3 个）

* `agent/application/ToolContract.java` + `ToolContractRegistry`：新增可选契约字段 `requires_confirmation: true`（YAML 顶层）。

* `AgentToolExecuteService.executeMediumTool`：在 `switch (run.mediumToolConfirmation())` **之前**先判 `contract.requiresConfirmation()`，命中则直接走审批分支（无论 ALLOW/ASK）。

* 新增 `start_test_run`（`QaService.createRun`）、`record_test_result`（`updateResult`，强制确认）、`complete_test_run`（`completeRun`，强制确认）。

* 改造 `forge-agent/tests/contracts/test_contract_registry.py::test_qa_skill_cannot_write_test_results_but_can_request_guarded_transition`，改为断言「QA 可以写测试结果，但 `record_test_result` / `complete_test_run` 带强制确认标记」。

### Phase 6 — Agent 使用体验（低成本高收益）

1. **落地** **`prompt_version`** **/** **`context_template`**：在各 skill YAML 增加 `guidance` 短文本（1–2 句「本阶段能做什么、不能做什么」），manifest 已下发，把它注入 `qwen.py::select_tool` 与 `finalize` / `stream_final` 的 system prompt。
2. **guardHints 语义化**：`select_tool` prompt 增加「观察含 `guardHints` 时必须向用户说明缺失项，不得重复同一动作」，避免模型盲重试到 `WorkflowGuardFailedException`。
3. **`_complete_action`** **泛化**：从硬编码 3 个工具名改为「任一成功 MEDIUM 写 + 结果含 `status`」，让 `advance_task` / `advance_bug` / `complete_test_run` 自动确定性收尾。
4. **终态错误停止重试**：`RUN_WORK_ITEM_MISMATCH`、`ACTION_NOT_ALLOWED_FOR_SKILL`、`WORKFLOW_GUARD_FAILED` 直接进 `finalize`。
5. **`create_plan`** **注入 effective tool 名列表**，让计划步骤可执行。

### Phase 7 — 前端（三页入口 + 上下文）

* `features/console/utils/requirement-context.ts`：把 `parseRequirementId` 泛化为 `parseAgentContext(pathname) → {kind:'requirement'|'bug', id} | undefined`（路由仅 `/requirements/:id*` 与 `/defects/:id` 可解析）。

* `stores/use-agent-panel-store.ts`：上下文从「仅 requirementId」扩展为 `{kind, id}`，供任务看板卡片显式设置（任务无详情路由）。

* `agent-command-drawer.tsx`：用新解析函数，展示「当前上下文：需求/缺陷」。

* `agent-conversation-panel.tsx`：`sendAgentMessage` 传上下文 id；在待审批区域展示 `availableActions` 与 `guardHints`（当前阶段可做什么、缺什么）。

* `requirement-list.tsx` / `task-board.tsx` / `defect-list.tsx`：行内/卡片加「交给 Agent」入口，写入上下文后 `setAgentPanelOpen(true)`。

* 同步 `agent-command-drawer.test.tsx`、`requirement-context.test.ts`。

### Phase 8 — 收尾

`make ci`（= `format-check lint contracts-check test build`）与 `git diff --check`。

## 五、验证方式

1. **契约**：`make contracts-check`（Python 侧 `validator.py` 校验全部 YAML 与 skill 引用）。
2. **单测/集成**：`ToolContractRegistryTest`（每 Skill `containsExactly` 精确列表）、`StageSkillRouterTest`、`AgentStageActionPolicyTest`、`ConversationServiceTest`、`InternalToolIntegrationTest`（LOW 不落 tool\_call / MEDIUM 幂等重放 / skill 白名单 / schema 三态）、`BugPersistenceIntegrationTest`、`DeliveryWorkflowIntegrationTest`。
3. **端到端**：跑通「新建需求 → 配四领域负责人与执行人 → 提交 PRD → 人工会签 → UX 建任务/文档 → 开发建任务/分支 → 测试建用例并记录结果 → 建缺陷 → 修复验证 → 发布」整条链路，其中仅会签/审核/验收/部署走人工。
4. **强制确认**：确认 `record_test_result` 在 `mediumToolConfirmation=ALLOW` 下仍停在 `WAITING_APPROVAL`，且前端审批抽屉能覆盖新工具（否则会出现「卡住」观感）。
5. 前端 `pnpm test` + 手测三页入口与上下文透传。

## 六、风险与注意事项

* **会红的测试（预期内）**：`StageSkillRouterTest`、`ConversationServiceTest`、`AgentStageActionPolicyTest`、`ToolContractRegistryTest`、`test_contract_registry.py`、`InternalToolIntegrationTest`、`agent-command-drawer.test.tsx`。

* **`WorkItemStatus`** **混合了需求/任务/缺陷三套状态**，路由必须只接受需求状态，不要用 `default` 兜底。

* **四票会签**：`DeliveryWorkflowPolicy.recordReview` 在 4 票齐前把目标状态改写为原状态，收紧后该逻辑不受影响，但需回归 `DeliveryWorkflowIntegrationTest`。

* **幂等键冲突**：`advance_task` 与 `advance_bug` 的 `BLOCK` 同名，务必带 toolName 与 itemId。

* **`requires_confirmation`** **是契约新增字段**，Java `ToolContractRegistry.toolContract(...)` 与 Python `registry.py` 都要解析，缺省为 false 以保持既有 20 个契约不变。

* **每新增一个 Tool 的注册清单（顺序固定，勿漏）**：① `packages/forge-contracts/tools/<name>.yaml`（`backend_mapping.path` 必须为 `/internal/v1/tools/<name>:execute`，与 `InternalToolController` 泛化路由一致，Controller 无需改动）；② 相关 `packages/forge-contracts/skills/*.yaml` 的 `allowed_tools`；③ `AgentToolExecuteService.executeApplicationService` 加 `case` 与私有 handler；④ 需要时 `AgentStageActionPolicy`；⑤ `ToolContractRegistryTest`；⑥ `forge-agent/tests/contracts/test_contract_registry.py`。

