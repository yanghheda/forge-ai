# 会话 29：Bug 状态机、开发回流与 QA Agent

## 我完成了什么

- 新增 `BUG` Work Item、严重级别与 `bug_details`，保存 Requirement、Test Run/Test Result 来源、复现步骤、修复证据和验证人。
- 建立 `OPEN → IN_PROGRESS → RESOLVED → VERIFIED → CLOSED` 固定状态机，并支持 `REOPEN` 与 `CANCEL`。
- `RESOLVE` 强制修复说明和 MR/Commit 证据；`VERIFY`、`CLOSE`、`REOPEN` 固定要求 `bug.verify`，Developer 默认没有该权限。
- 新增 `QA_FAIL`，由 QA 把 Requirement 从 `IN_QA` 回流到 `IN_DEVELOPMENT`。
- Bug 创建在同一事务中写入工作项、详情及 `FOUND_IN`/`FIXED_BY` 关系；迁移追加事件并使用 Work Item version 防并发覆盖。
- 新增 QA Skill、`create_test_case` 和 `create_bug` MEDIUM Tool。Agent 只创建草稿，allowlist 不包含执行结果写入。
- QA 页面支持从 FAIL Test Result 创建关联 Bug，并展示严重级别、状态及下一步修复/验证动作。

## 核心概念

`RESOLVED` 是研发关于“修复已提交”的主张，必须附证据；`VERIFIED` 是 QA 对该主张的独立复测事实。二者分开后，Developer 不能用一次状态更新同时成为修复者和验收者，reopen 也能明确表达回归失败，而不是覆盖历史。

Bug 仍是 Work Item，因此编号、权限资源、事件和 Delivery Graph 复用统一聚合；Bug 特有的测试来源与修复验证事实放在 `bug_details`。Test Result 是人工执行事实，Agent 生成的 Case/Bug 永远只是草稿或工作项，不能把 `NOT_RUN` 改为 `PASS`。

## 调用链

人工创建与迁移：

`QaPanel → BugController → BugService → PermissionEvaluator/BugWorkflowRegistry → WorkItemStore + BugStore → MyBatis → MySQL`

QA Agent：

`Agent Run → Tool Registry/Schema/Skill → Permission/Policy → MEDIUM 审批与幂等 → QaService 或 BugService → MySQL`

开发回流：

`RequirementDetail → RequirementTransitionService → RequirementWorkflowRegistry(QA_FAIL) → RequirementTransitionStore → work_items/work_item_events`

## 数据与事务边界

- MySQL 是 Bug 状态、来源关系、修复证据、验证人及迁移事件的唯一事实来源。
- `BugService.create` 外层短事务包含原子编号、BUG Work Item、`bug_details` 和关系写入；任何来源 scope 校验失败都会整体回滚。
- Bug 迁移先由纯状态机确定目标和权限，再以 `status + version + workspace_id + project_id` 条件更新，并在同一事务更新详情和追加事件。
- QA Agent 的 MEDIUM 写操作复用既有 `runId:toolCallId` 幂等键；没有外部系统调用，也不持有数据库行锁调用 GitLab。
- Delivery Graph 读取统一 Work Item/Relation 投影，不复制 Bug 数据，不改变图的深度与节点上限。

## 风险与遗留问题

- UI 的 RESOLVE 证据当前是最小占位输入，生产体验应让 Developer 选择已有 MR/Commit；本轮服务端已强制非空，但尚未校验 URL 对应本项目 GitLab 事实。
- Bug 可关联 Test Result、Requirement 和可选 Dev Task；MR/Commit 目前保存在审计证据数组，结构化外键关联需要后续有明确设计再演进。
- BLOCKER/CRITICAL 未验证集合已可由 severity/status 确定，但 Release 阻断查询与 Precheck 属于会话 30，本轮不提前实现。
- Agent Eval 是确定性契约门禁，不是在线模型质量评测；真实模型回归仍需要稳定的评测运行环境。

## 复盘问题

1. 为什么 `RESOLVED` 与 `VERIFIED` 必须分开，且 Developer 默认不能执行 `VERIFY`？
2. Bug 如何利用 Requirement、Test Result、Dev Task 与修复证据形成可追踪链，同时避免复制事实？
3. QA Agent 最适合生成哪些草稿，为什么最终测试结果与 QA 放行不能交给 Agent？
