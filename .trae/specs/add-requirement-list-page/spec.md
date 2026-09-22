# 需求列表页 Spec

## Why

当前需求只能通过「我的需求」（服务端 mine 过滤）浏览，缺少一个能看到全部需求的列表入口，也无法在列表内直接完成轻量维护动作（调整优先级、期望发布时间、删除草稿需求）。同时需求详情页挂在全局仪表盘之下，层级归属不清晰。

本次改动新增独立的「需求列表」页，只承载 Requirement 本体，并让需求详情成为需求列表的二级页。

## What Changes

- forge-web 新增 `/requirements` 需求列表页与主导航项「需求列表」，列出系统中全部 Requirement，**不包含** Requirement 下创建的 UX / DEV / QA Task 与 Bug。
- 列表行提供简单操作：
  - 修改优先级与期望发布时间（复用既有 `PATCH /api/v1/work-items/{id}`，携带 `expectedVersion`）。
  - 删除需求（**仅草稿 DRAFT** 可删，需新增后端接口，软删除）。
- 列表行点击进入需求详情 `/requirements/[id]`；详情页返回入口改为「← 返回需求列表」；面包屑显示「需求列表 / 需求详情」；进入详情及其下游阶段页时，主导航仍高亮「需求列表」（体现二级页归属）。
- forge-server 新增 `DELETE /api/v1/requirements/{requirementId}`，软删除 Requirement，约束为：`requirement.edit` 权限 + 仅 `DRAFT` 状态 + `expectedVersion` 乐观锁。
- 「我的需求」页本轮**不改动**（后续会单独改造为缺陷列表），其列表行进入需求详情后同样归入「需求列表」层级的二级页。

已知限制（本次不做）：
- 「期望发布时间」只支持设置/修改，不支持清空：后端 `PATCH` 语义中 `dueAt = null` 表示保留当前值。
- 删除不做回收站/恢复入口；删除后该需求在列表、详情与统计中不再可见（`deleted_at` 软删除）。
- 列表内不做批量操作、不做状态流转（状态流转仍在详情页与任务工作区完成）。

## Impact

- 影响规格：需求浏览与检索、需求基础字段维护、需求详情导航层级。
- 影响代码：
  - forge-web：`src/app/requirements/page.tsx`（新增路由）、`src/features/work-item/components/requirement-list-page.tsx`（新增页组件）、`src/features/work-item/components/requirement-list.tsx`、`requirement-list.module.css`、`src/features/work-item/api/work-item-api.ts`、`src/components/app-shell/app-shell.tsx`、`src/features/work-item/components/organization-requirement-detail.tsx`、`src/features/work-item/index.ts`。
  - forge-server：`RequirementController`、`OrganizationRequirementService`、`OrganizationRequirementStore`、`MybatisOrganizationRequirementStore`、`OrganizationRequirementMapper`，以及新的集成测试。
- 不引入多公司/多租户语义：本实例为单实例单公司部署（ADR-016），列表中不存在「切换/筛选公司」这类能力，也不需要为多公司数据范围做任何新增设计。
- 不影响既有授权与事实边界：`forge-server` 仍是业务事实、状态机与授权的唯一权威；新增删除接口不新增权限点，复用既有 `requirement.edit`；Mapper SQL 继续显式限制 `organization_id`（ADR-016 要求，单公司部署不能作为无范围查询的理由）。

## ADDED Requirements

### Requirement: 需求列表页

系统 SHALL 在主导航提供「需求列表」入口（`/requirements`），展示系统中的 Requirement，且列表数据只包含 `type = REQUIREMENT` 的工作项。

#### Scenario: 打开需求列表

- **WHEN** 已登录成员进入 `/requirements`
- **THEN** 页面标题为「需求列表」，主导航「需求列表」高亮
- **AND** 列表只展示 Requirement 行（编号、标题、优先级、状态、更新时间），不出现 UX / DEV / QA Task 或 Bug
- **AND** 列表支持关键字搜索与状态筛选，并展示需求总数

#### Scenario: 单公司实例的数据可见性

- **WHEN** 任意已登录成员打开需求列表
- **THEN** 列表展示该实例中的需求数据，不提供公司切换或公司筛选
- **AND** 列表请求不携带 `mine` 参数，默认展示全部需求

### Requirement: 列表内轻量操作

系统 SHALL 允许在需求列表行内修改需求的优先级与期望发布时间，并携带该行读取到的 `version` 作为乐观锁。

#### Scenario: 修改优先级与期望发布时间成功

- **WHEN** 用户在列表行操作区选择「修改优先级 / 期望发布时间」并提交
- **THEN** 前端调用 `PATCH /api/v1/work-items/{id}`，请求体只包含变更字段与 `expectedVersion`
- **AND** 成功后提示「需求已更新」，并刷新列表数据

#### Scenario: 版本冲突

- **WHEN** 提交时 `expectedVersion` 与数据库当前 `version` 不一致
- **THEN** 服务端返回 409，前端以错误提示展示，并刷新列表以便用户基于最新数据重试

### Requirement: 删除需求

系统 SHALL 提供 Requirement 软删除能力，仅允许删除 `DRAFT` 状态的需求。

#### Scenario: 删除草稿需求成功

- **WHEN** 用户在列表中对 `DRAFT` 状态需求确认删除
- **THEN** 前端调用 `DELETE /api/v1/requirements/{requirementId}?expectedVersion={version}`
- **AND** 服务端校验 `requirement.edit` 权限、需求类型为 `REQUIREMENT`、状态为 `DRAFT`、版本匹配后写入 `deleted_at`
- **AND** 返回成功，前端提示「需求已删除」并刷新列表

#### Scenario: 非草稿需求不可删除

- **WHEN** 需求状态不是 `DRAFT`（已进入评审或后续阶段）
- **THEN** 前端不展示删除入口
- **AND** 若仍被调用，服务端返回 409 且不改变任何数据

#### Scenario: 删除成功后不可见

- **WHEN** 需求被软删除
- **THEN** 该需求不出现在需求列表、详情与统计中
- **AND** 再次读取或删除该需求返回 404

#### Scenario: 并发安全

- **WHEN** 删除时传入的 `expectedVersion` 与数据库当前 `version` 不一致
- **THEN** 服务端返回 409，且不改变任何数据
- **AND** 删除 SQL 显式携带 `organization_id`、`type = 'REQUIREMENT'`、`deleted_at IS NULL` 与 `version` 条件（单公司部署下 `organization_id` 仍按 ADR-016 要求保留）

### Requirement: 需求详情二级页导航

系统 SHALL 将需求详情视为需求列表的二级页。

#### Scenario: 从列表进入详情

- **WHEN** 用户在需求列表点击某一行
- **THEN** 跳转到 `/requirements/{id}`
- **AND** 出现在详情页头部的返回入口为「← 返回需求列表」，指向 `/requirements`

#### Scenario: 详情页层级归属

- **WHEN** 用户停留在 `/requirements/{id}` 或其下游阶段页（文档、UX、开发、QA、发布）
- **THEN** 面包屑显示「工作台 / 需求详情」，主导航「需求列表」保持高亮

## MODIFIED Requirements

### Requirement: 需求详情返回入口

需求详情的返回入口 SHALL 指向需求列表，而非全局仪表盘。

#### Scenario: 返回列表

- **WHEN** 用户在需求详情点击返回入口
- **THEN** 跳转到 `/requirements`（需求列表页）

## REMOVED Requirements

无。
