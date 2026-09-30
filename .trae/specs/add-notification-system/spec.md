# 站内通知系统 Spec

## Why

系统已有 `notifications` 表、通知读取接口（列表 / 未读数 / 标记已读）以及右上角铃铛抽屉与未读角标，但**全仓没有任何写入方**：审批、指派、需求流转等需要他人行动的事件发生后，责任方收不到提示，只能自行逐页查找；Agent HIGH 风险审批更是缺少面向审批人的处理入口（执行轨迹页仅限 Run 发起人访问），通知点击也不会跳转。

## What Changes

- forge-server 新增 `notification` 模块，成为 `notifications` 表的唯一读写方：现有 3 个通知端点从 `ConsoleController` 迁入（**路径与响应形状不变**），新增统一的写入能力与收件人反查能力。
- 接入 3 类通知生产者，全部与业务变更在同一事务内写入（业务回滚则通知不产生）：
  1. **审批类**：Agent HIGH 风险 Tool 审批、部署 HIGH 审批；
  2. **指派类**：需求 / 任务 / 缺陷创建或改派负责人；需求关联领域负责人与执行人（净新增时）；
  3. **需求流转交接**：评审请求、评审结论、阶段交接、QA 回流、退回、验收。
- 新增 `GET /api/v1/approvals?status=PENDING` 待审批列表端点（公司范围，要求 `approval.decide`），forge-web 新增 `/approvals` 待审批页面，审批通知深链直达并可批准 / 驳回。
- forge-web 通知抽屉：点击通知标记已读并深链跳转到业务页面；按类型展示标签；顶栏订阅用户级 SSE 近实时刷新未读数与列表，并保留 30 秒轮询兜底（含后台标签页）。
- 不新增 Flyway 迁移：`notifications` 表、`idx_notifications_user_unread` 与 `idx_approvals_organization_status_expiry` 均已存在，无需改表。

**非目标**：Pipeline 构建失败通知、审批过期通知、通知偏好 / 免打扰、邮件或飞书外发、"全部已读"。这些留作后续迭代。

## Impact

- Affected specs：通知与未读数（`docs/testing/full-project-manual-acceptance.md` HOME-03）、审批与人工暂停（`ARCH` 13.3–13.4、`DETAIL` 9.7）、Work Item 指派与需求状态机（`DETAIL` 4.4、5）、控制台模块边界（`DETAIL` 13）。
- Affected code：
  - forge-server 新增 `notification/{controller,application,domain,infrastructure/persistence}`
  - `console/controller/ConsoleController.java`、`console/application/{ConsoleService,ConsoleStore,ConsoleModels}.java`、`console/infrastructure/persistence/ConsoleMapper.java`：移除通知读写
  - `agent/application/ApprovalService.java`、`agent/application/ApprovalStore.java`、`agent/infrastructure/persistence/ApprovalMapper.java`、`agent/controller/ApprovalController.java`
  - `release/application/DeploymentService.java`
  - `workitem/application/WorkItemCommandService.java`、`workitem/application/RequirementTransitionService.java`
  - forge-web：`components/app-shell/app-shell.tsx`、新增 `features/console/components/notification-list.tsx` 与 `features/console/model/notification-link.ts`、`features/agent-run/api/agent-run-api.ts`、新增 `features/agent-run/components/pending-approvals.tsx` 与 `views/approvals-view.tsx`、`routing/app-router.tsx`
- 无 ADR：不改事实来源、认证、事务边界、Agent Tool 安全边界与部署拓扑；通知仍在既有业务事务内写入既有表。

## 触发行为分析（结论）

通知只发给"需要行动或需要知晓结果"的责任方，收件人解析分三种策略：

| 策略 | 收件人来源 |
|---|---|
| 明确字段 | `work_items.assignee_user_id`、`approvals.requested_by`、`deployments.requested_by`、`work_items.reporter_user_id` |
| 需求领域角色 | `requirement_participants`（负责人 = 持有 `{role}_ADMIN` 的参与者）与 `requirement_executors`（执行人） |
| 权限反查 | 持有指定权限码（如 `approval.decide`）的公司有效成员 |

统一约束：收件人去重、排除操作者本人、无收件人不写入、只发本 `organization_id` 内的有效成员。

### 通知类型与跳转目标

| type | 触发场景 | 收件人 | resourceType / resourceId | web 跳转 |
|---|---|---|---|---|
| `APPROVAL_REQUESTED` | Agent HIGH 风险 Tool 审批请求 | 持有 `approval.decide` 的成员（排除发起人） | `APPROVAL` / approvalId | `/approvals` |
| `APPROVAL_DECIDED` | Agent Tool 审批通过 / 拒绝 | 发起人 `requested_by` | `AGENT_RUN` / runId | `/agent/trace` |
| `DEPLOYMENT_APPROVAL_REQUESTED` | 人工发起的部署 HIGH 审批 | 持有 `approval.decide` 的成员（排除申请人） | `REQUIREMENT_RELEASE` / requirementId | `/requirements/:id/release` |
| `DEPLOYMENT_APPROVAL_DECIDED` | 部署审批通过 / 拒绝 | 申请人 `requested_by` | `REQUIREMENT_RELEASE` / requirementId | `/requirements/:id/release` |
| `WORK_ITEM_ASSIGNED` | 需求 / 任务 / 缺陷创建或改派负责人 | 新负责人 | 见下方任务目标映射 | 对应页面 |
| `WORK_ITEM_ASSIGNED` | 需求关联领域负责人 / 执行人（全量替换中的净新增） | 新关联成员 | `REQUIREMENT` / requirementId | `/requirements/:id` |
| `REVIEW_REQUESTED` | 需求进入评审、等待会签确认 | 对应领域负责人 | `REQUIREMENT` / requirementId | `/requirements/:id` |
| `REQUIREMENT_HANDOFF` | 阶段交接、QA 回流、退回、验收 | 下一责任方 | `REQUIREMENT`、`REQUIREMENT_UX`、`REQUIREMENT_DEVELOPMENT`、`REQUIREMENT_RELEASE` / requirementId | 对应阶段页 |

任务指派跳转目标：`REQUIREMENT` → `/requirements/:id`；`UX_TASK` → `REQUIREMENT_UX` → `/requirements/:id/ux`；`DEV_TASK` → `REQUIREMENT_DEVELOPMENT` → `/requirements/:id/development`；`QA_TASK` → `REQUIREMENT_QA` → `/requirements/:id/qa`；`BUG` → `DEFECT` → `/defects/:id`。

需求流转动作 → 通知映射（仅在该动作真实推进状态且非幂等重放时发送）：

| 动作 | 类型 | 收件人 | 目标 |
|---|---|---|---|
| `SUBMIT_PRODUCT_REVIEW` | `REVIEW_REQUESTED` | 产品负责人 | `REQUIREMENT` |
| `START_PRODUCT_REVIEW` | `REVIEW_REQUESTED` | 四领域负责人（排除操作者） | `REQUIREMENT` |
| `APPROVE_PRODUCT_REVIEW`（收齐四方票并推进） | `REQUIREMENT_HANDOFF` | UX 负责人 | `REQUIREMENT` |
| `SUBMIT_UX_REVIEW` | `REVIEW_REQUESTED` | UX 负责人 | `REQUIREMENT` |
| `APPROVE_UX_REVIEW` | `REQUIREMENT_HANDOFF` | 开发负责人 | `REQUIREMENT` |
| `SUBMIT_FOR_QA` | `REQUIREMENT_HANDOFF` | QA 负责人 | `REQUIREMENT` |
| `QA_FAIL` | `REQUIREMENT_HANDOFF` | 开发领域成员（负责人 ∪ 执行人，排除操作者） | `REQUIREMENT_DEVELOPMENT` |
| `QA_PASS` | `REQUIREMENT_HANDOFF` | 产品领域成员（排除操作者） | `REQUIREMENT` |
| `ACCEPT_REQUIREMENT` | `REQUIREMENT_HANDOFF` | 开发负责人 | `REQUIREMENT_RELEASE` |
| `REJECT_REQUIREMENT`、`REJECT_PRODUCT_REVIEW` | `REQUIREMENT_HANDOFF` | 需求报告人（排除操作者） | `REQUIREMENT` |
| `REJECT_UX_REVIEW` | `REQUIREMENT_HANDOFF` | UX 领域成员（排除操作者） | `REQUIREMENT_UX` |
| 其他动作（`APPROVE_REQUIREMENT`、`START_UX`、`START_DEVELOPMENT`、`START_QA`、未推进的 `APPROVE_PRODUCT_REVIEW` 等） | — | 不通知 | — |

## ADDED Requirements

### Requirement: 通知模块归属与读写边界

`notifications` 表 SHALL 只由 `notification` 模块读写；通知读取保持"仅接收者本人可见"的公司内隔离。

现有 3 个端点路径与响应形状保持不变，由 `NotificationController` 提供：

- `GET /api/v1/notifications?limit=30`：默认 30，取值夹取 1..100，按 `created_at DESC, id DESC` 返回本人通知；
- `GET /api/v1/notifications/unread-count`：返回 `{"count": n}`；
- `POST /api/v1/notifications/{id}/read`：204，幂等，非本人或不存在返回 404。

#### Scenario: 只能读取本人通知
- **WHEN** 用户请求通知列表或未读数
- **THEN** 只返回其 `organization_id` + `user_id` 下的通知，其他成员的通知不可见

#### Scenario: 标记已读幂等
- **WHEN** 用户对同一条通知重复调用标记已读
- **THEN** 两次都成功（204），`read_at` 保持首次时间

#### Scenario: 越权标记已读
- **WHEN** 用户尝试标记他人通知或不存在通知
- **THEN** 返回 404，且不修改任何行

#### Scenario: 控制台不再直连通知表
- **WHEN** 检查 `console` 模块代码
- **THEN** `ConsoleController`、`ConsoleService`、`ConsoleMapper` 不再包含通知读写（搜索、看板等其余能力不变）

### Requirement: 通知写入与收件人解析

系统 SHALL 提供统一的写入能力：与触发它的业务变更处于同一事务；对收件人去重；排除操作者本人；无有效收件人时不写入；标题与正文按列宽（255 / 2000）截断，避免因文案超长回滚业务。

系统 SHALL 提供收件人反查能力，所有 SQL 显式携带 `organization_id`，且只返回 `organization_members.status='ACTIVE'` 与 `users.status='ACTIVE'` 的成员：

- 按权限码反查候选成员（当前用于 `approval.decide`）；
- 按需求领域角色反查负责人（`requirement_participants` ⋈ `{role}_ADMIN`）与领域成员（负责人 ∪ `requirement_executors`）。

#### Scenario: 业务回滚不产生通知
- **WHEN** 业务变更在同一事务内失败回滚
- **THEN** 对应的通知记录不存在

#### Scenario: 收件人去重与排除操作者
- **WHEN** 反查结果包含重复用户或操作者本人
- **THEN** 每个用户最多写入一条通知，且不写入操作者

#### Scenario: 跨公司隔离
- **WHEN** 反查收件人
- **THEN** 只返回该 `organization_id` 下的有效成员，不返回其他公司成员

### Requirement: 审批通知（Agent HIGH Tool 审批）

`ApprovalService` SHALL 在以下节点写入通知：

- `request` 首次创建且 `riskLevel = HIGH`：通知持有 `approval.decide` 的成员（排除发起人），type `APPROVAL_REQUESTED`，resource `APPROVAL`/approvalId；
- `decide` 通过或拒绝：通知发起人，type `APPROVAL_DECIDED`，resource `AGENT_RUN`/runId。

`riskLevel = MEDIUM` 由发起人本人在会话内确认，SHALL NOT 产生通知；`cancel`（仅发起人可撤销）与过期 SHALL NOT 产生通知。

#### Scenario: HIGH 审批请求通知候选审批人
- **WHEN** Agent 发起一个 HIGH 风险 Tool 审批
- **THEN** 公司内持有 `approval.decide` 的成员（不含发起人）各收到一条 `APPROVAL_REQUESTED` 通知

#### Scenario: MEDIUM 确认不通知
- **WHEN** Agent 发起一个 MEDIUM 风险 Tool 审批
- **THEN** 不写入任何通知

#### Scenario: 幂等重放不重复通知
- **WHEN** 同一 `(runId, toolCallId)` 的审批请求被重放（命中唯一键）
- **THEN** 不新增审批记录，也不新增通知

#### Scenario: 审批结果通知发起人
- **WHEN** 审批被批准或被拒绝
- **THEN** 发起人收到一条 `APPROVAL_DECIDED` 通知，正文说明通过或被拒

### Requirement: 部署审批通知

`DeploymentService` SHALL 在人工部署申请（`HUMAN_REQUEST`）创建时通知持有 `approval.decide` 的成员（排除申请人），type `DEPLOYMENT_APPROVAL_REQUESTED`；在 `decide` 通过或拒绝时通知申请人，type `DEPLOYMENT_APPROVAL_DECIDED`；两者 resource 均为 `REQUIREMENT_RELEASE` + Release 关联的需求 id。

`AGENT_TOOL` 路径（已由 Agent HIGH 审批覆盖、创建即批准）与部署审批过期 SHALL NOT 产生通知。

#### Scenario: 人工部署申请通知审批人
- **WHEN** 用户通过发布页发起部署（HUMAN_REQUEST）
- **THEN** 持有 `approval.decide` 的成员（不含申请人）各收到一条待审批通知，点击跳到该需求发布页

#### Scenario: 部署审批结果通知申请人
- **WHEN** 部署审批被批准或被拒绝
- **THEN** 申请人收到结果通知

#### Scenario: Agent 路径不重复通知
- **WHEN** 部署由已批准的 Agent 审批创建（AGENT_TOOL）
- **THEN** 不写入部署待审批通知

### Requirement: 工作项指派通知

`WorkItemCommandService` SHALL 在工作项负责人被设置或变更为他人时通知新负责人，type `WORK_ITEM_ASSIGNED`，resource 按任务类型映射到对应页面。

- 需求创建（`create`）携带负责人：通知新负责人；
- 任务 / 缺陷创建（`createTask`、`createUxTask`）：通知新负责人；
- 更新（`update`）：仅当负责人实际发生变化时通知。

负责人未变化、指派给自己（操作者 = 新负责人）或未指派 SHALL NOT 产生通知。

#### Scenario: 创建任务指派负责人
- **WHEN** 领域负责人创建 UX / 开发 / QA 任务或缺陷并指派执行人
- **THEN** 被指派人收到一条通知，点击跳到对应阶段页或缺陷详情页

#### Scenario: 改派通知新负责人
- **WHEN** 工作项负责人从 A 变更为 B（B ≠ 操作者）
- **THEN** B 收到通知，A 不收到

#### Scenario: 重复保存同一负责人不通知
- **WHEN** 更新请求携带的负责人与当前负责人相同
- **THEN** 不写入通知

### Requirement: 需求关联通知（领域负责人 / 执行人）

`OrganizationRequirementService` SHALL 在 `replaceParticipants`（四领域负责人全量替换）与 `replaceExecutors`（某领域执行人全量替换）成功后，仅对**净新增**的关联成员写入通知：type `WORK_ITEM_ASSIGNED`，target `REQUIREMENT` + requirementId，排除操作者本人；移除与未变化的关联 SHALL NOT 产生通知。

该要求覆盖两个入口：需求详情页（`RequirementController`）与 Agent 工具（`AgentToolExecuteService`）；需求创建时自动把创建人登记为产品负责人属于自关联，自然不产生通知。

#### Scenario: 新关联领域负责人
- **WHEN** 产品负责人把某成员关联为需求的 UX 负责人
- **THEN** 该成员收到一条通知，正文说明被关联为该需求的 UX 负责人，点击跳到需求详情页

#### Scenario: 更换领域负责人
- **WHEN** 某领域负责人从 A 更换为 B（B ≠ 操作者）
- **THEN** B 收到通知，A 不新增通知

#### Scenario: 新关联执行人
- **WHEN** 领域负责人为某领域关联新的执行人
- **THEN** 净新增的执行人各收到一条通知，正文说明被关联为该需求对应领域的执行人

#### Scenario: 全量替换的未变化项不重复通知
- **WHEN** 重新保存与他人相同的负责人 / 执行人集合
- **THEN** 未变化的成员不产生重复通知

#### Scenario: 移除关联不通知
- **WHEN** 替换时移除了某成员（负责人置空或执行人被移除）
- **THEN** 不写入任何通知

### Requirement: 需求流转交接通知

`RequirementTransitionService` SHALL 在流转成功（非幂等重放）且状态真正推进时，按"流转动作 → 通知映射"通知下一责任方。

未集齐四方会签票的 `APPROVE_PRODUCT_REVIEW`（状态未变化）SHALL NOT 发送"评审通过"类通知；会签待办由 `START_PRODUCT_REVIEW` 触发。

#### Scenario: 进入评审通知负责人
- **WHEN** 需求被提交公司审核或进入产品评审 / UX 评审
- **THEN** 对应领域负责人收到 `REVIEW_REQUESTED` 通知

#### Scenario: 阶段交接通知下一责任方
- **WHEN** 需求推进到 READY_FOR_UX、READY_FOR_DEV、READY_FOR_QA、READY_FOR_RELEASE 或进入验收
- **THEN** 下一阶段负责人收到 `REQUIREMENT_HANDOFF` 通知

#### Scenario: QA 回流通知开发
- **WHEN** QA 执行 `QA_FAIL`
- **THEN** 开发负责人与开发执行人（排除操作者）收到通知，点击跳到开发阶段页

#### Scenario: 退回通知报告人
- **WHEN** 需求审核被驳回或产品评审被退回（回到 DRAFT）
- **THEN** 需求报告人（若 ≠ 操作者）收到通知

#### Scenario: 幂等重放不重复通知
- **WHEN** 同一 `idempotencyKey` 的流转被重放
- **THEN** 直接返回既有结果，不写入新通知

### Requirement: 待审批入口（Server + Web）

Server SHALL 提供 `GET /api/v1/approvals?status=PENDING`：要求当前用户持有 `approval.decide`，返回本公司 `PENDING` 且 `requested_by ≠ 当前用户` 的审批，按 `expires_at` 升序；返回工具名、风险等级、发起人（含显示名）、原因、过期时间与 `version`，**不返回**冻结参数（`frozen_arguments_encrypted` / `argument_hash` / `resource_versions_json`）。

forge-web SHALL 新增 `/approvals` 页面：展示待审批列表与剩余有效期，支持批准 / 驳回（复用现有 `POST /api/v1/approvals/{id}:decide` 与 `expectedVersion` 乐观锁），操作成功后刷新待审批列表与通知未读数；侧边栏「Agent」分组提供"待审批"入口。

#### Scenario: 审批人处理待办
- **WHEN** 审批人从通知或侧边栏进入 `/approvals`，点击"批准"
- **THEN** 审批被提交，列表移除该条，未读数随之刷新

#### Scenario: 无权用户被拒绝
- **WHEN** 不持有 `approval.decide` 的用户请求待审批列表
- **THEN** 请求被拒绝（沿用仓库既有 deny-by-default 语义：抛出 `ResourceNotFoundException`），不泄漏任何审批内容

#### Scenario: 不返回本人发起的审批
- **WHEN** 用户请求待审批列表
- **THEN** 结果不含 `requested_by` 为自己的审批（HIGH 不可自批）

#### Scenario: 并发决策冲突
- **WHEN** 两名审批人同时对同一条审批操作
- **THEN** 后提交者收到版本冲突错误，页面提示并刷新列表

### Requirement: Web 通知抽屉深链与类型展示

通知抽屉 SHALL 在点击通知时先标记已读（未读时）再按 `resourceType`/`resourceId` 跳转，并关闭抽屉；无法映射跳转目标的通知只标记已读。抽屉 SHALL 展示通知类型标签与未读视觉区分。

#### Scenario: 点击待审批通知
- **WHEN** 点击一条 `APPROVAL_REQUESTED` 通知
- **THEN** 该通知被标记已读，抽屉关闭，页面跳转到 `/approvals`

#### Scenario: 点击指派通知
- **WHEN** 点击一条任务指派通知
- **THEN** 跳转到对应需求阶段页或缺陷详情页

#### Scenario: 无跳转目标
- **WHEN** 通知的 `resourceType` 为空或无法识别
- **THEN** 仅标记已读，不跳转，不抛错

#### Scenario: 未读数刷新
- **WHEN** 用户标记已读或完成审批操作
- **THEN** `["notifications"]` 相关查询被失效，未读角标立即更新；无操作时由用户级 SSE 近实时推送，并保留 30 秒轮询兜底

### Requirement: 通知实时推送（SSE）

系统 SHALL 提供用户级通知事件流 `GET /api/v1/notifications/events`（`text/event-stream`），按登录用户与公司范围推送新通知事件，使接收者在无需手动刷新页面的情况下近实时看到未读角标与通知列表更新。

- 鉴权与隔离：使用会话 Cookie 解析用户与公司；未登录返回 401；只推送该用户本公司的通知。
- 补发与去重：支持 `afterId` 查询参数与 `Last-Event-ID` 请求头作为游标，从游标之后按 `id` 升序至少一次补发；已推送过的事件不重复推送（重连按游标续传）。
- 心跳与生命周期：空闲时发送心跳事件，连接超时或客户端断开即结束该连接，不改变任何业务状态。
- 兜底：SSE 不可用时前端退化为 30 秒轮询（并允许后台标签页继续轮询），不因推送失败而阻断页面。

#### Scenario: 新通知近实时到达
- **WHEN** 其他成员产生一条发给当前登录用户的通知
- **THEN** 该用户的未读角标与（若抽屉打开）通知列表在数秒内自动更新，无需手动刷新页面

#### Scenario: 断线重连按游标续传
- **WHEN** SSE 连接中断后自动重连
- **THEN** 客户端携带 `afterId` / `Last-Event-ID`，只收到游标之后的通知，不重复推送已收到的事件

#### Scenario: 未登录被拒绝
- **WHEN** 未携带有效会话请求通知事件流
- **THEN** 返回 401，不建立事件流

#### Scenario: 只推送本人公司的通知
- **WHEN** 同一会话监听事件流
- **THEN** 只收到 `organization_id` + `user_id` 与当前登录身份一致的通知，其他成员或其他公司的通知不出现

#### Scenario: 推送失败退化为轮询
- **WHEN** 事件流无法建立或反复中断
- **THEN** 页面不报错、不阻断操作，未读数仍由 30 秒轮询兜底更新

## MODIFIED Requirements

### Requirement: 控制台通知读写归属（`DETAIL` 13 / ConsoleController）

控制台模块 SHALL 不再直接读写 `notifications` 表；通知的 3 个既有端点由 `notification` 模块的 `NotificationController` 提供，路径与响应形状保持不变；控制台保留搜索、看板与创建入口等能力。

### Requirement: 通知与未读数验收（HOME-03）

接入生产者后，HOME-03「通知与未读数」的"制造通知"路径为：发起 HIGH 审批 / 人工部署申请、创建或改派工作项负责人、流转需求状态；核对列表、未读数、标记已读与刷新时，未读数准确、只能读取本人可见通知、标记已读幂等。

## REMOVED Requirements

无。
