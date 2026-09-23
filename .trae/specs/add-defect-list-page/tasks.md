# Tasks

- [x] Task 1: forge-server 缺陷查询扩展为公司范围并补齐创建人/时间字段
  - [x] SubTask 1.1: `BugMapper.find` 与 `BugMapper.findByRequirement` 增加 `item.reporter_user_id`、`reporter.display_name reporter_name`、`item.created_at`、`item.updated_at`，并加 `LEFT JOIN users reporter ON reporter.id = item.reporter_user_id`；新增 `findAll(organizationId)` 查询：同样的字段与 JOIN，条件为 `item.organization_id = #{organizationId} AND item.type = 'BUG' AND item.deleted_at IS NULL`，`ORDER BY item.item_number DESC`（不新增分页）
  - [x] SubTask 1.2: `BugView` 增加 `reporterUserId`（创建人标识）、`reporterName`（创建人展示名，可空）、`createdAt`、`updatedAt` 组件，按 AGENTS 规则为每个新增 record 组件补紧邻声明的中文块注释；同步修改 `withActions` 与 `MybatisBugStore.view` 的映射（`reporter_name` 用可空文本，时间为 `LocalDateTime` → UTC `Instant` 转换，与 `ConsoleService` 既有做法一致）
  - [x] SubTask 1.3: `BugStore` 增加 `List<BugView> findAll(long organizationId)` 契约，`MybatisBugStore` 实现
  - [x] SubTask 1.4: `BugService.list` 签名改为 `list(long userId, long organizationId, Long requirementId)`：`requirementId == null` 时走 `store.findAll`，否则走 `store.findByRequirement`；权限仍为 `permissions.requireOrganization(..., "bug.read")`
  - [x] SubTask 1.5: `BugController.list` 的 `requirementId` 改为可选查询参数（`@RequestParam(required = false) Long requirementId`），返回类型保持 `List<BugView>`；Controller 之外不新增 Swagger 注解
  - [x] SubTask 1.6: 新增集成测试 `forge-server/src/test/java/ai/forge/server/qa/DefectListIntegrationTest.java`（继承 `InfrastructureIntegrationTestBase`，`@Transactional`）：断言省略 `requirementId` 时返回跨多个需求的缺陷且按编号倒序；断言返回的 `reporterName` 与 `createdAt` 非空；断言携带 `requirementId` 时只返回该需求缺陷；断言不返回其他公司缺陷与已软删除缺陷

- [x] Task 2: forge-web 删除「我的需求」
  - [x] SubTask 2.1: 删除 `src/views/my-requirements-view.tsx`、`src/features/work-item/components/my-requirements.tsx`、`my-requirements.module.css`、`my-requirements.test.tsx`（删除前用 Grep 确认无其他引用）
  - [x] SubTask 2.2: `src/routing/app-router.tsx` 删除 `/my-requirements` 路由与 `MyRequirementsView` import
  - [x] SubTask 2.3: `src/features/work-item/index.ts` 删除 `MyRequirements` 导出
  - [x] SubTask 2.4: `requirement-list.tsx` 去掉 `mine` prop 与 queryKey 的 `mine/all` 分支（查询 key 固定为全量），同步删除「我的需求」相关注释；`work-item-api.ts` 的 `listOrganizationRequirements` 去掉 `mine` 过滤参数与 `mine=true` 拼接（服务端 `/v1/requirements?mine=` 与后端集成测试保持不变）
  - [x] SubTask 2.5: `src/features/console/components/task-board.tsx` 页头「进入需求创建任务」链接由 `/my-requirements` 改为 `/requirements`

- [x] Task 3: forge-web 缺陷 API 客户端扩展
  - [x] SubTask 3.1: `work-item-api.ts` 的 `BugView` 增加 `reporterUserId`、`reporterName`、`createdAt`、`updatedAt` 字段
  - [x] SubTask 3.2: 新增 `listCompanyBugs(client?)`：`GET /v1/bugs`（不携带 `requirementId`），返回 `BugView[]`
  - [x] SubTask 3.3: 新增 `getBug(bugId, client?)`：`GET /v1/bugs/{bugId}`，返回 `BugView`
  - [x] SubTask 3.4: `work-item-api.test.ts` 补充断言：`listCompanyBugs` 请求路径为 `/v1/bugs` 且不含查询参数；`getBug(7)` 请求路径为 `/v1/bugs/7`

- [x] Task 4: forge-web 新增缺陷列表页
  - [x] SubTask 4.1: 修正 `src/features/work-item/utils/requirement-analytics.ts` 的 `bugStatusLabel` 为五种真实状态文案（`TODO` 待修复、`IN_PROGRESS` 修复中、`BLOCKED` 阻塞中、`WAITING_VERIFICATION` 待验证、`DONE` 已完成），保持 `bugStatusOrder` 不变；确认 `requirement-analytics-dashboard.tsx` 缺陷分布图沿用该映射
  - [x] SubTask 4.2: 新增 `src/features/work-item/components/defect-list.module.css`：页头、状态统计窄条（阻塞用错误色、已完成用成功色）、工具栏、行式列表（表头 + 行）、严重级别与状态标签、空态与总数，视觉沿用现有设计系统（Arco 组件 + CSS Module，参考 `task-board.module.css` 与 `requirement-list.module.css`）
  - [x] SubTask 4.3: 新增 `src/features/work-item/components/defect-list.tsx` 导出 `DefectList`：页头「缺陷列表」+ 说明；查询 `listCompanyBugs()`、当前用户 `getCurrentUser()`、成员 `listRequirementMembers()`；顶部五项状态计数；工具栏「类型」（全部 / 我的）、「状态」（五种 + 全部，文案取 `bugStatusLabel`）、「修复人」（`showSearch`，按 `displayName` 或 `email` 过滤，含「全部」）；列表行展示编号、标题、严重级别（文案取 `severityLabel`）、状态标签、修复人、创建人、创建时间
  - [x] SubTask 4.4: 过滤逻辑：类型「我的」按 `assigneeUserId === 当前用户 id`；状态按 `status`；修复人按 `assigneeUserId`；三者取交集，统计窄条与「共 N 条缺陷」均基于过滤后集合；加载中显示 `Spin`、失败显示 `Alert` + `formatRequestError`、空集合显示空态文案
  - [x] SubTask 4.5: 行标题链接到 `/defects/{id}`；新增 `src/views/defect-list-view.tsx`（`ProtectedApp` 包裹）并在 `app-router.tsx` 注册 `/defects`；`features/work-item/index.ts` 导出 `DefectList`

- [x] Task 5: forge-web 新增缺陷详情页
  - [x] SubTask 5.1: 新增 `src/features/work-item/components/defect-detail.module.css` 与 `defect-detail.tsx` 导出 `DefectDetail`：查询 `getBug(id)`（`id` 来自路由参数）；返回入口「← 返回缺陷列表」指向 `/defects`；头部展示编号、标题、状态标签、严重级别标签
  - [x] SubTask 5.2: 详情内容：元信息区（状态、严重级别、修复人、创建人、关联需求（链接 `/requirements/{requirementId}`）、关联开发任务（`devTaskKey`，缺失显示「无」）、创建时间、更新时间）；缺陷内容区（复现步骤有序列表、预期结果、实际结果）；修复信息区（修复说明、修复证据列表，缺失显示「暂无」）
  - [x] SubTask 5.3: 只读约束：不渲染任何状态操作按钮，不调用 `transitionBug`
  - [x] SubTask 5.4: 新增 `src/views/defect-detail-view.tsx`（`ProtectedApp` 包裹）并在 `app-router.tsx` 注册 `/defects/:id`；`features/work-item/index.ts` 导出 `DefectDetail`

- [x] Task 6: forge-web 导航与页面标题接入缺陷列表
  - [x] SubTask 6.1: `app-shell.tsx` 将导航项「我的需求」替换为「缺陷列表」（`/defects`，`IconBug`），`active` 判定为 `pathname === "/defects" || pathname.startsWith("/defects/")`
  - [x] SubTask 6.2: `pageName` 增加 `/defects` → 「缺陷列表」、`pathname.startsWith("/defects/")` → 「缺陷详情」，并删除 `/my-requirements` 分支
  - [x] SubTask 6.3: `app-shell.test.tsx` 更新导航断言：存在「缺陷列表」链接且 `href` 为 `/defects`；不再存在「我的需求」链接

- [x] Task 7: forge-web 缺陷列表与详情测试
  - [x] SubTask 7.1: 新增 `defect-list.test.tsx`：默认三个筛选均为「全部」并请求 `/v1/bugs`；选择「我的」后仅展示修复人为当前用户的缺陷；选择状态后仅展示该状态缺陷；按姓名选择修复人后过滤生效；组合筛选后统计窄条与「共 N 条」与列表行数一致；状态标签使用「待修复/修复中/阻塞中/待验证/已完成」且不出现原始状态码；行标题链接指向 `/defects/{id}`；接口失败时展示错误提示
  - [x] SubTask 7.2: 新增 `defect-detail.test.tsx`：渲染复现步骤、预期结果、实际结果、修复说明与修复证据；展示创建人与修复人；返回链接指向 `/defects`；关联需求链接指向 `/requirements/{requirementId}`；不出现状态操作按钮；接口 404 时展示错误提示

- [x] Task 8: 端到端验证与质量门禁
  - [x] SubTask 8.1: forge-server 运行 `DefectListIntegrationTest` 与既有 QA / Console / Requirement 相关集成测试
  - [x] SubTask 8.2: forge-web 运行受影响 Vitest 用例（缺陷列表、缺陷详情、导航、需求列表、API 客户端）
  - [x] SubTask 8.3: 根目录运行 `make ci` 与 `git diff --check`，记录结果（含已存在的 pre-existing 失败项与复现证据）

# Task Dependencies

- Task 3 depends on Task 1（DTO 字段与接口路径确定后才能接入客户端）
- Task 4 depends on Task 3 与 Task 6.1（列表页需要 `/defects` 导航位与 API 客户端）；Task 4.1 与 Task 3 无耦合，可先行
- Task 5 depends on Task 3
- Task 6 depends on Task 2（删除「我的需求」后才能腾出导航位）与 Task 4.5 / Task 5.4（路由存在后标题才有意义）
- Task 7 depends on Task 4、Task 5
- Task 8 depends on Task 1、Task 2、Task 3、Task 4、Task 5、Task 6、Task 7
- Task 1 与 Task 2 无耦合，可并行
