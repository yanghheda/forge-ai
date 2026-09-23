# 缺陷列表与缺陷详情页 Spec

## Why

「我的需求」与「需求列表」都是需求浏览入口，功能重叠；同时缺陷目前只能在单个需求的 QA / 开发工作区内查看，开发与测试缺少一个跨需求、按状态与修复人聚焦的统一入口。本次删除「我的需求」，新增「缺陷列表」与「缺陷详情」页，主要服务开发与测试角色。

## What Changes

- **BREAKING**（仅前端内部）删除「我的需求」：主导航项、路由 `/my-requirements`、视图、页面组件、样式与测试一并移除；`RequirementList` 的 `mine` 分支与前端 API 客户端的 `mine` 过滤参数随之删除。
- 新增主导航「缺陷列表」（`/defects`）与二级页「缺陷详情」（`/defects/{id}`），占用「我的需求」原来的导航位置。
- 缺陷列表筛选三项：类型（全部 / 我的 = 修复人是当前用户）、状态（待修复 / 修复中 / 阻塞中 / 待验证 / 已完成）、修复人（公司有效成员，按姓名或邮箱搜索）。
- 状态沿用统一任务状态 `TODO`/`IN_PROGRESS`/`BLOCKED`/`WAITING_VERIFICATION`/`DONE`，仅在缺陷语境下使用缺陷文案；缺陷状态文案收敛到 `bugStatusLabel` 单一来源。
- 缺陷详情页只读展示：缺陷内容（复现步骤、预期结果、实际结果）、修复信息（修复说明、修复证据）、创建人与修复人、关联需求与关联开发任务；不提供状态流转入口（流转仍在需求 QA / 开发工作区完成）。
- 后端 `GET /api/v1/bugs` 的 `requirementId` 改为可选：省略时返回公司范围内全部未删除缺陷；`BugView` 增加创建人与时间字段。
- 本次不做：新建缺陷、批量操作、分页、缺陷编辑或删除、导出、按需求或严重级别筛选、列表内状态流转。

## Impact

- Affected specs: 缺陷浏览与检索（新增）、需求浏览入口（移除 mine 分支）、forge-web 控制台导航与页面标题、Bug 读取 DTO。
- Affected code:
  - forge-server：`qa/controller/BugController.java`、`qa/application/BugService.java`、`qa/application/BugStore.java`、`qa/application/BugView.java`、`qa/infrastructure/persistence/BugMapper.java`、`qa/infrastructure/persistence/MybatisBugStore.java`、`src/test/java/ai/forge/server/qa/DefectListIntegrationTest.java`（新增）
  - forge-web：`src/routing/app-router.tsx`、`src/components/app-shell/app-shell.tsx`、`src/components/app-shell/app-shell.test.tsx`、`src/views/my-requirements-view.tsx`（删除）、`src/views/defect-list-view.tsx`（新增）、`src/views/defect-detail-view.tsx`（新增）、`src/features/work-item/index.ts`、`src/features/work-item/api/work-item-api.ts`、`src/features/work-item/api/work-item-api.test.ts`、`src/features/work-item/components/my-requirements.tsx` 与 `my-requirements.module.css` 与 `my-requirements.test.tsx`（删除）、`src/features/work-item/components/requirement-list.tsx`、`src/features/work-item/components/defect-list.tsx` 与 `defect-list.module.css`（新增）、`src/features/work-item/components/defect-detail.tsx` 与 `defect-detail.module.css`（新增）、`src/features/work-item/components/defect-list.test.tsx` 与 `defect-detail.test.tsx`（新增）、`src/features/work-item/utils/requirement-analytics.ts`、`src/features/console/components/task-board.tsx`
- 事实来源不变：`forge-server` 仍是缺陷读取与授权的唯一权威；不新增表、不新增 Flyway 迁移、不新增权限点（复用 `bug.read`）。
- 权限：缺陷列表与详情均要求 `bug.read`（当前由 `OWNER`/`ADMIN`/`DEVELOPER`/`QA` 持有），与「主要给开发和测试使用」一致；不满足时服务端 403。

## ADDED Requirements

### Requirement: 缺陷列表页

系统 SHALL 在主导航提供「缺陷列表」入口（`/defects`），列出公司范围内全部缺陷（`type = BUG` 的工作项），并仅对持有 `bug.read` 权限的成员返回数据。

#### Scenario: 打开缺陷列表

- **WHEN** 已登录成员进入 `/defects`
- **THEN** 页面标题为「缺陷列表」，主导航「缺陷列表」高亮
- **AND** 列表只展示缺陷行（编号、标题、严重级别、状态、修复人、创建人、创建时间），不出现需求、设计任务、开发任务、测试任务
- **AND** 顶部展示五种缺陷状态的计数

#### Scenario: 无权限

- **WHEN** 当前成员不具备 `bug.read`（例如仅 `PRODUCT` 或 `UX` 角色）
- **THEN** 服务端返回 403，前端以错误提示展示，不渲染缺陷数据

### Requirement: 缺陷状态文案单一来源

缺陷 SHALL 复用统一任务状态，并在缺陷语境下使用固定中文文案：`TODO` 待修复、`IN_PROGRESS` 修复中、`BLOCKED` 阻塞中、`WAITING_VERIFICATION` 待验证、`DONE` 已完成；该文案 SHALL 由 `bugStatusLabel` 单一来源提供。

#### Scenario: 列表与详情文案一致

- **WHEN** 某缺陷状态为 `WAITING_VERIFICATION`
- **THEN** 列表与详情均展示「待验证」，不展示原始状态码

#### Scenario: 状态计数使用同一文案

- **WHEN** 顶部统计窄条渲染
- **THEN** 五项标签依次为「待修复 / 修复中 / 阻塞中 / 待验证 / 已完成」

### Requirement: 缺陷筛选

工具栏 SHALL 提供三个筛选：类型（全部 / 我的）、状态（五种缺陷状态 + 全部）、修复人（公司有效成员 + 全部，支持按姓名或邮箱搜索）；三者 SHALL 可同时生效并取交集。

#### Scenario: 默认筛选

- **WHEN** 用户首次打开缺陷列表
- **THEN** 类型为「全部」、状态为「全部」、修复人为「全部」
- **AND** 列表展示全部缺陷

#### Scenario: 选择「我的」

- **WHEN** 用户选择类型「我的」
- **THEN** 仅展示修复人（`assigneeUserId`）为当前登录用户的缺陷

#### Scenario: 选择状态

- **WHEN** 用户选择状态「待修复」
- **THEN** 仅展示 `status = TODO` 的缺陷

#### Scenario: 按姓名筛选修复人

- **WHEN** 用户在修复人下拉中输入姓名片段并选择某成员
- **THEN** 仅展示修复人为该成员的缺陷

#### Scenario: 组合筛选与计数联动

- **WHEN** 类型、状态与修复人同时生效
- **THEN** 列表仅展示同时满足三个条件的缺陷
- **AND** 顶部五种状态计数与「共 N 条缺陷」均基于过滤后的集合计算

### Requirement: 缺陷详情页

系统 SHALL 提供 `/defects/{id}` 缺陷详情页，只读展示缺陷完整信息。

#### Scenario: 从列表进入详情

- **WHEN** 用户在缺陷列表点击某一行
- **THEN** 跳转到 `/defects/{id}`
- **AND** 返回入口为「← 返回缺陷列表」并指向 `/defects`
- **AND** 面包屑显示「工作台 / 缺陷详情」，主导航「缺陷列表」保持高亮

#### Scenario: 展示缺陷内容与人员

- **WHEN** 详情加载完成
- **THEN** 展示编号、标题、状态、严重级别、修复人、创建人、关联需求、关联开发任务、创建时间与更新时间
- **AND** 展示复现步骤（有序列表）、预期结果、实际结果
- **AND** 修复说明与修复证据缺失时展示「暂无」占位，而非空白或 `null`

#### Scenario: 只读

- **WHEN** 详情页渲染
- **THEN** 不出现开始修复、提交修复、验证、打回、阻塞等状态操作入口
- **AND** 关联需求以链接形式跳转到 `/requirements/{requirementId}`

#### Scenario: 缺陷不存在

- **WHEN** 缺陷不存在、已被删除或不属于当前公司
- **THEN** 服务端返回 404，页面以错误提示展示

### Requirement: 公司范围缺陷查询

`GET /api/v1/bugs` SHALL 支持省略 `requirementId`：省略时返回公司范围内全部未删除缺陷，按工作项编号倒序；`BugView` SHALL 增加 `reporterUserId`、`reporterName`、`createdAt`、`updatedAt`。

#### Scenario: 公司范围查询

- **WHEN** 成员持有 `bug.read` 且请求中省略 `requirementId`
- **THEN** 返回该公司全部未删除缺陷，跨需求聚合，按 `item_number` 倒序
- **AND** 每条缺陷包含创建人展示名 `reporterName` 与创建时间 `createdAt`

#### Scenario: 不跨公司与不返回已删除

- **WHEN** 请求公司范围缺陷
- **THEN** 查询 SQL 显式包含 `organization_id`、`type = 'BUG'` 与 `deleted_at IS NULL`
- **AND** 不返回其他公司或已软删除的缺陷

## MODIFIED Requirements

### Requirement: 按需求读取缺陷

`GET /api/v1/bugs?requirementId=` SHALL 保持既有行为与权限（`bug.read`），并同样返回新增的创建人与时间字段。

#### Scenario: QA 工作区不受影响

- **WHEN** 需求 QA 工作区或开发工作区按需求读取缺陷
- **THEN** 返回结构对既有调用方保持兼容，仅新增字段

### Requirement: 缺陷状态文案在全局仪表盘生效

全局仪表盘的缺陷分布图 SHALL 使用与缺陷列表一致的 `bugStatusLabel` 文案，不再出现原始状态码。

#### Scenario: 缺陷分布图标签

- **WHEN** 全局仪表盘渲染缺陷分布
- **THEN** 图例与坐标轴展示「待修复 / 修复中 / 阻塞中 / 待验证 / 已完成」

## REMOVED Requirements

### Requirement: 我的需求页

**Reason**: 与「需求列表」功能重叠，导航位让给缺陷列表。

**Migration**: 删除 `/my-requirements` 路由与 `MyRequirements` 组件（含样式与测试）；导航项替换为「缺陷列表」；`RequirementList` 去掉 `mine` 分支，前端 `listOrganizationRequirements` 去掉 `mine` 过滤参数；任务看板页头「进入需求创建任务」入口改指向 `/requirements`；服务端 `GET /v1/requirements?mine=` 参数与既有集成测试保持不变。
