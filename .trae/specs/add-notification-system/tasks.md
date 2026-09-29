# Tasks

- [x] Task 1: forge-server 建立 notification 模块并迁移通知读取
  - [x] SubTask 1.1: 新增 `notification/domain`：`NotificationType`（7 种类型码）与跳转目标类型常量 / 枚举（`REQUIREMENT`、`REQUIREMENT_UX`、`REQUIREMENT_DEVELOPMENT`、`REQUIREMENT_QA`、`REQUIREMENT_RELEASE`、`DEFECT`、`APPROVAL`、`AGENT_RUN`），每个成员带中文块注释
  - [x] SubTask 1.2: 新增 `notification/application`：`NotificationStore` 端口、`NotificationService`（写入 + 收件人反查 + list/unread/markRead）、`NotificationView`（保持现有 JSON 形状：id/type/title/body/resourceType/resourceId/readAt/createdAt）
  - [x] SubTask 1.3: 新增 `notification/infrastructure/persistence/MybatisNotificationMapper`：通知读写 SQL（全部显式带 `organization_id`）、按权限码反查有效成员、按需求领域角色反查负责人与领域成员
  - [x] SubTask 1.4: 新增 `notification/controller/NotificationController`：3 个端点路径与行为保持不变（`limit` 默认 30 夹取 1..100、未读数、标记已读幂等且越权 404）
  - [x] SubTask 1.5: 从 `console` 模块移除通知读写（`ConsoleController`、`ConsoleService`、`ConsoleStore`、`ConsoleModels`、`ConsoleMapper`），确认无残留引用
  - [x] SubTask 1.6: 集成测试（Testcontainers）：本人可见、未读数准确、重复标记已读幂等、越权 404、跨公司不可见；写入侧：收件人去重、排除操作者、无收件人不写入、业务回滚不产生通知（13 例全绿）
  - 验证：已运行 `NotificationIntegrationTest` 与 `ModuleArchitectureTest`（13 + 4 例全绿）及 `TaskBoard*` 回归；`/api/v1/notifications` 响应形状与 web 客户端兼容

- [x] Task 2: 审批通知生产（Agent HIGH 审批 + 部署审批）
  - [x] SubTask 2.1: `ApprovalService.request`：`HIGH` 时向 `approval.decide` 持有者（排除发起人）写入 `APPROVAL_REQUESTED`；`MEDIUM` 不通知；唯一键重放不重复通知
  - [x] SubTask 2.2: `ApprovalService.decide`：通过 / 拒绝后向发起人写入 `APPROVAL_DECIDED`（resource = `AGENT_RUN`/runId；发起人自确认时因排除操作者而不通知）
  - [x] SubTask 2.3: `DeploymentService.create`（仅 `HUMAN_REQUEST`）与 `decide`：写入 `DEPLOYMENT_APPROVAL_REQUESTED` / `DEPLOYMENT_APPROVAL_DECIDED`，resource = `REQUIREMENT_RELEASE` + Release 关联需求 id；`AGENT_TOOL` 路径不通知
  - [x] SubTask 2.4: 测试：HIGH 请求通知候选审批人且不含发起人、MEDIUM 无通知、重放无重复、结果通知发起人、Agent 部署路径不重复通知（新增 `ApprovalNotificationIntegrationTest` 8 例全绿）
  - 验证：已运行 `ApprovalNotificationIntegrationTest`、`NotificationIntegrationTest`、`ModuleArchitectureTest`、`ProductionJavaMemberCommentTest` 及 agent/release 既有回归（全部通过）；通知文案与跳转资源字段符合 spec 表

- [x] Task 3: 工作项指派通知
  - [x] SubTask 3.1: `WorkItemCommandService.create`（需求创建携带负责人）、`createTask` / `createUxTask`（任务与缺陷创建）、`update`（负责人变更）写入 `WORK_ITEM_ASSIGNED`
  - [x] SubTask 3.2: 按任务类型映射跳转目标：需求 → `REQUIREMENT`，UX_TASK → `REQUIREMENT_UX`，DEV_TASK → `REQUIREMENT_DEVELOPMENT`，QA_TASK → `REQUIREMENT_QA`，BUG → `DEFECT` + 缺陷 id
  - [x] SubTask 3.3: 测试：创建即指派通知、改派只通知新负责人、负责人未变化或自指派不通知（新增 `AssignmentNotificationIntegrationTest` 8 例全绿）
  - 验证：已运行 `AssignmentNotificationIntegrationTest`、`NotificationIntegrationTest`、`ModuleArchitectureTest` 与 `TaskBoard*` 回归（全部通过）

- [x] Task 4: 需求流转交接通知
  - [x] SubTask 4.1: `RequirementTransitionService.transition` 在 `transitionStore.transition` 成功后按「动作 → 收件人 → 目标」映射写入 `REVIEW_REQUESTED` / `REQUIREMENT_HANDOFF`
  - [x] SubTask 4.2: 实现映射：`SUBMIT_PRODUCT_REVIEW`、`START_PRODUCT_REVIEW`、`APPROVE_PRODUCT_REVIEW`（仅推进时）、`SUBMIT_UX_REVIEW`、`APPROVE_UX_REVIEW`、`SUBMIT_FOR_QA`、`QA_FAIL`、`QA_PASS`、`ACCEPT_REQUIREMENT`、`REJECT_REQUIREMENT`、`REJECT_PRODUCT_REVIEW`、`REJECT_UX_REVIEW`；其余动作不通知
  - [x] SubTask 4.3: 测试：各映射收件人正确、幂等重放不重复、未集齐会签票的 `APPROVE_PRODUCT_REVIEW` 不发送评审通过通知、操作者被排除（新增 `RequirementHandoffNotificationIntegrationTest` 10 例全绿，a–h 全覆盖）
  - 验证：已运行 `RequirementHandoffNotificationIntegrationTest`、`NotificationIntegrationTest`、`ModuleArchitectureTest` 与 `DeliveryWorkflowIntegrationTest`、`RequirementTransitionIntegrationTest` 回归（全部通过）

- [x] Task 5: 待审批闭环（Server 端点 + Web 页面）
  - [x] SubTask 5.1: `ApprovalStore` / `ApprovalMapper` 新增 `findPendingByOrganization`（`organization_id` + `status='PENDING'` + `requested_by ≠ 当前用户`，按 `expires_at` 升序，JOIN users 取发起人显示名）
  - [x] SubTask 5.2: `ApprovalController` 新增 `GET /api/v1/approvals?status=PENDING`：要求 `approval.decide`（无权按仓库 deny-by-default 语义拒绝），返回 id/toolName/riskLevel/requestedBy/requestedByName/reason/expiresAt/version，不返回冻结参数
  - [x] SubTask 5.3: Web 新增 `listPendingApprovals` API（`features/agent-run/api/agent-run-api.ts`，`decideApproval` 首参收窄为 `{ id, version }`），复用既有 `decideApproval`
  - [x] SubTask 5.4: Web 新增 `/approvals` 页面（`views/approvals-view.tsx` + `features/agent-run/components/pending-approvals.tsx`）：列表、剩余有效期、批准 / 驳回（含确认）、冲突与无权提示、操作后刷新
  - [x] SubTask 5.5: 路由与导航：`app-router.tsx` 注册 `/approvals`，侧边栏「Agent」分组新增"待审批"入口（按 `approval.decide` 对应角色展示），`pageName` 增加映射
  - [x] SubTask 5.6: 测试：Server 权限与过滤（无权被拒绝、排除本人发起、不返回冻结字段，5 例全绿）；Web 列表渲染、批准调用参数（id + expectedVersion）、空态与错误提示（4 例全绿）
  - 验证：已运行 `PendingApprovalIntegrationTest`、`ApprovalNotificationIntegrationTest`、`NotificationIntegrationTest`、`ModuleArchitectureTest`、`AgentRunIntegrationTest`、`InternalToolIntegrationTest` 与 web 全量 vitest（49 文件 234 例）、`typecheck`、`lint`（全部通过）

- [x] Task 6: Web 通知抽屉深链与类型标签
  - [x] SubTask 6.1: 新增 `features/console/model/notification-link.ts`：`resourceType` + `resourceId` → 路由的纯函数映射（`APPROVAL`→`/approvals`、`AGENT_RUN`→`/agent/trace`、`REQUIREMENT*`→需求页与阶段页、`DEFECT`→`/defects/:id`），无法识别返回 null（另含 `notificationTypeLabel` 类型标签映射）
  - [x] SubTask 6.2: 新增 `features/console/components/notification-list.tsx`：类型标签、未读视觉、点击先标记已读再跳转并关闭抽屉
  - [x] SubTask 6.3: `app-shell.tsx` 接入新组件，保持未读数 30 秒轮询与操作后失效刷新
  - [x] SubTask 6.4: 测试：映射函数单测、点击待审批 / 指派通知的跳转与标记已读、无目标仅标记已读（14 例全绿）
  - 验证：已运行 vitest 相关 3 个测试文件（14 例全绿）、`npm run typecheck`、`npm run lint`

- [x] Task 7: 端到端回归与验收
  - [x] SubTask 7.1: 运行 `make ci` 与 `git diff --check`，修复所有失败（三轮后全绿：format-check / lint / contracts-check / test（forge-server 401 例 + forge-web 234 例）/ build；修复内容仅为新文件补文件末尾换行）
  - [x] SubTask 7.2: HOME-03 三条制造路径由自动化等价验证覆盖：HIGH 审批与人工部署申请（`ApprovalNotificationIntegrationTest`）、指派负责人（`AssignmentNotificationIntegrationTest`）、需求流转（`RequirementHandoffNotificationIntegrationTest`）；列表 / 未读数 / 本人可见 / 标记已读幂等由 `NotificationIntegrationTest` 覆盖。真实浏览器人工验收待用户在本机环境执行
  - [x] SubTask 7.3: 复核安全边界：通知文案不含冻结参数与敏感字段、跨公司不可见、越权 404、待审批列表按仓库 deny-by-default 语义拒绝；发现 `WorkItemCommandService.create` 的事务缺口（见 Task 8）

- [x] Task 8: 修复 `WorkItemCommandService.create` 的「创建 + 指派通知」事务原子性（验证阶段发现）
  - [x] SubTask 8.1: 给 `create` 增加 `@Transactional`（与 `createTask` / `update` 保持一致），使需求创建与指派通知在同一事务内提交或回滚；覆盖 `POST /api/v1/work-items` 与 Agent `create_requirement` 两条直连路径
  - [x] SubTask 8.2: 增加回归保护：在 `AssignmentNotificationIntegrationTest` 中用反射断言 `create` 带 `@Transactional`（注释说明理由是保证创建 + 通知同事务），并确认既有 8 例仍全绿（现 9 例）
  - 验证：已重跑 `AssignmentNotificationIntegrationTest`（9 例）、`NotificationIntegrationTest`（13 例）、`ModuleArchitectureTest`（4 例）全绿；完整 `make ci` 复跑确认中

# Task Dependencies

- Task 2、Task 3、Task 4、Task 5 依赖 Task 1（写入与收件人反查能力）；其中 Task 5 的 Server 部分可与 Task 1 并行开发
- Task 6 与 Task 2–5 相互独立，可并行（依赖既有读取 API 与前端映射函数）
- Task 7 依赖 Task 1–6 全部完成
- Task 8 依赖 Task 3（同文件与同测试类），完成后需重新确认 checklist 第 5 条
