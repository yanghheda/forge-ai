# Checklist

> 验证方式：静态逐条核对（文件 + 行号证据）＋ 自动化测试（`NotificationIntegrationTest` 13 例、`ApprovalNotificationIntegrationTest` 8 例、`AssignmentNotificationIntegrationTest` 9 例、`RequirementHandoffNotificationIntegrationTest` 10 例、`PendingApprovalIntegrationTest` 5 例、web vitest 234 例）＋ 完整 `make ci` 门禁。

- [x] `notifications` 表的读写全部经由 `notification` 模块，`console` 模块不再读写该表，`ConsoleController` 搜索与看板等其余能力不变（`git diff` 确认 console 的通知端点、Service/Store/Mapper/Models 均已移除）
- [x] 3 个通知端点路径与响应形状保持不变：`GET /api/v1/notifications`、`GET /api/v1/notifications/unread-count`、`POST /api/v1/notifications/{id}/read`（`NotificationController` 默认 `limit=30` 夹取 1..100，`NotificationView` 字段与迁移前逐字段一致）
- [x] 通知只对接收者本人可见与可标记已读，越权或不存在返回 404（`markRead` 影响行数 ≠1 抛 `ResourceNotFoundException`；SQL 限定 `organization_id + user_id`；`NotificationIntegrationTest` 覆盖）
- [x] 重复标记已读保持成功（幂等），`read_at` 不被覆盖（`SET read_at=COALESCE(read_at,UTC_TIMESTAMP(6))`；测试断言连续两次调用与 `read_at` 不变）
- [x] 通知写入与业务变更同事务，业务回滚后不残留通知（4 个生产者均在被 `@Transactional` 包裹的方法内；`NotificationIntegrationTest` 用事务回滚断言 0 记录；验证阶段发现并修复 `WorkItemCommandService.create` 缺 `@Transactional` 的事务缺口，见 Task 8，并补反射回归断言）
- [x] 收件人去重、排除操作者本人、无收件人不写入，所有反查 SQL 显式携带 `organization_id` 且只返回 ACTIVE 成员（`NotificationService` 去重/排除/空集合早退；反查 SQL 均带 `organization_id` 与 `om.status='ACTIVE' AND u.status='ACTIVE'`）
- [x] 通知标题 / 正文按列宽（255 / 2000）截断，超长文案不会导致业务事务失败（`NotificationService.TITLE_MAX/BODY_MAX`；测试断言截断结果）
- [x] Agent HIGH 审批请求后，持有 `approval.decide` 的成员（不含发起人）收到 `APPROVAL_REQUESTED`，resource 为 `APPROVAL` + approvalId（`ApprovalService.notifyRequested`；测试覆盖含无权限成员与发起人为 0 条）
- [x] MEDIUM 风险确认不产生通知；审批唯一键重放不产生重复通知（仅 `contract.highRisk()` 写入；重放分支早退；测试覆盖）
- [x] 审批通过 / 拒绝后发起人收到 `APPROVAL_DECIDED`，resource 为 `AGENT_RUN` + runId（`ApprovalService.decide`；发起人自确认因排除操作者而不通知；测试覆盖）
- [x] 人工部署申请后审批候选人收到 `DEPLOYMENT_APPROVAL_REQUESTED`；`AGENT_TOOL` 路径不重复通知；部署审批结果通知申请人（`DeploymentService.notifyRequested/notifyDecided`；测试覆盖三条路径）
- [x] 需求 / 任务 / 缺陷创建或改派负责人后，新负责人收到 `WORK_ITEM_ASSIGNED`；负责人未变化、自指派、未指派均不通知（`WorkItemCommandService.notifyAssignee`；测试覆盖）
- [x] 任务指派通知的跳转目标按类型映射正确（需求详情 / UX / 开发 / QA 阶段页 / 缺陷详情）（`assignmentTarget`：REQUIREMENT / REQUIREMENT_UX / REQUIREMENT_DEVELOPMENT / REQUIREMENT_QA / DEFECT；测试断言 resource_type 与 resource_id）
- [x] 需求流转按映射表通知下一责任方（评审负责人、阶段负责人、QA 回流开发、退回报告人、验收后开发负责人）（`RequirementTransitionService.notifyHandoff`；`RequirementHandoffNotificationIntegrationTest` 10 例覆盖 a–h）
- [x] 未集齐四方会签票的 `APPROVE_PRODUCT_REVIEW` 不发送"评审通过"通知；幂等重放不产生重复通知（`result.status()==current.status()` 早退；重放分支提前返回；测试覆盖）
- [x] `GET /api/v1/approvals?status=PENDING` 要求 `approval.decide`（无权时按仓库 deny-by-default 语义拒绝，不泄漏审批内容），排除本人发起的审批，按 `expires_at` 升序，不返回冻结参数（SQL 只 SELECT 契约 8 列；`PendingApprovalIntegrationTest` 5 例含 record 组件集合断言）
- [x] `/approvals` 页面可批准 / 驳回（携带 `expectedVersion`），成功后刷新列表与未读数，冲突时给出可理解的提示（`pending-approvals.tsx`：`Modal.confirm` + `decideApproval({id,version}, organizationId, decision)`，成功失效 `["pending-approvals"]` 与 `["notifications"]`，冲突展示版本冲突中文提示；组件测试覆盖调用参数）
- [x] 通知抽屉点击未读通知先标记已读再跳转并关闭抽屉；无法映射目标时仅标记已读且不抛错（`notification-list.tsx`：mark（失败不阻断）→ invalidate → navigate → onClose；组件测试覆盖三种点击）
- [x] 抽屉展示通知类型标签与未读视觉区分；未读数保持 30 秒轮询，操作后立即失效刷新（`notificationTypeLabel` + 未读圆点/已读降透明度样式；`app-shell.tsx` `refetchInterval: 30_000`）
- [x] 未读数为 0 时角标不显示数字；新增导航入口与路由 `pageName` 显示正确（Arco `Badge count=0 dot` 不渲染数字；侧边栏「待审批」入口按 OWNER/ADMIN/DEVELOPER_ADMIN 展示；`pageName("/approvals")` = 待审批；`app-shell.test.tsx` 断言入口 href）
- [x] `make ci` 与 `git diff --check` 全绿（Task 7 完整门禁三轮后全绿：format-check / lint / contracts-check / test（forge-server 401 例、forge-web 234 例）/ build；Task 8 修复后再次运行确认）
- [x] 手工验收 HOME-03：三条"制造通知"路径（HIGH 审批与人工部署申请、指派负责人、需求流转）与列表 / 未读数 / 标记已读 / 刷新均有自动化等价覆盖并通过；真实浏览器点击验收待用户在本机环境执行