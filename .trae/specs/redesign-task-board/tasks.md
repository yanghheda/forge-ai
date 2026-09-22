# Tasks

- [x] Task 1: forge-server 看板接口补齐卡片字段
  - [x] SubTask 1.1: `ConsoleMapper.board` SQL 在既有字段外增加 `wi.priority`、`wi.due_at`、`wi.blocked_reason`，并以 `LEFT JOIN users assignee ON assignee.id = wi.assignee_user_id` 取 `assignee.display_name AS assignee_name`；保持 `organization_id`、`deleted_at IS NULL`、`type IN ('UX_TASK','DEV_TASK','QA_TASK','BUG')` 与 `ORDER BY lane,position,wi.id` 不变
  - [x] SubTask 1.2: `ConsoleModels.BoardItem` 增加 `priority`、`dueAt`、`assigneeName`、`blockedReason` 组件，按 AGENTS 规则为每个组件补紧邻声明的中文块注释（`dueAt`/`assigneeName`/`blockedReason` 语义含空值含义）
  - [x] SubTask 1.3: `ConsoleService.board` 映射新增字段（`due_at` 仍按 `LocalDateTime` → UTC `Instant` 转换，`assignee_name`/`blocked_reason` 为可空文本）
  - [x] SubTask 1.4: 先写集成测试 `forge-server/src/test/java/ai/forge/server/console/TaskBoardIntegrationTest.java`（继承 `InfrastructureIntegrationTestBase`，`@Transactional`）：断言带优先级/截止日期/执行人的任务在 `console.board(...)` 中返回 `priority`、`dueAt`、`assigneeName`；断言未分配执行人的任务 `assigneeName` 为空；断言看板返回集合不含 `REQUIREMENT` 与已删除工作项

- [x] Task 2: forge-server 新增「按执行人查询需求选项」接口
  - [x] SubTask 2.1: `ConsoleStore` 增加 `List<Map<String,Object>> requirementsByParticipant(long organizationId, Long userId)` 契约
  - [x] SubTask 2.2: `ConsoleMapper.requirementsByParticipant` 新增可审查 SQL：`SELECT wi.id, wi.item_key, wi.title FROM work_items wi WHERE wi.organization_id=#{organizationId} AND wi.type='REQUIREMENT' AND wi.deleted_at IS NULL <if userId != null>AND (EXISTS(requirement_participants ...) OR EXISTS(requirement_executors ...))</if> ORDER BY wi.item_number DESC LIMIT 200`（`userId` 为空表示返回公司全部需求）
  - [x] SubTask 2.3: `ConsoleModels` 增加 `RequirementOption` record（`id`/`itemKey`/`title`），字段携带中文块注释
  - [x] SubTask 2.4: `ConsoleService.boardRequirements(long userId, Long assigneeUserId)` 实现：解析当前公司 scope → `permissionEvaluator.requireOrganization(..., "task.read")`（与看板同权限，避免出现「能看板但拿不到筛选项」）→ 返回选项
  - [x] SubTask 2.5: `ConsoleController` 增加 `GET /task-board/requirements`，查询参数 `assigneeUserId`（可选，`@Positive`）；Controller 之外禁止新增 Swagger 注解
  - [x] SubTask 2.6: 在 Task 1.4 的集成测试中补充：只返回该执行人作为协作人（`requirement_participants` 或 `requirement_executors`）的需求；不返回他人独占需求；不返回其他公司需求

- [x] Task 3: forge-web 扩展控制台 API 客户端
  - [x] SubTask 3.1: `console-api.ts` 的 `BoardItem` 增加 `priority`、`dueAt`、`assigneeName`、`blockedReason` 字段
  - [x] SubTask 3.2: `console-api.ts` 新增 `RequirementOption` 类型与 `listBoardRequirements(assigneeUserId?, client?)`，请求 `GET /v1/task-board/requirements`（`assigneeUserId` 为空时不拼接该查询参数）
  - [x] SubTask 3.3: 新增/补充 `console-api` 测试，断言请求路径与查询参数拼接

- [x] Task 4: forge-web 新增 TaskBoard 页面组件并按设计稿重做样式
  - [x] SubTask 4.1: 新增 `features/console/components/task-board.module.css`，按设计稿实现：页面标题区、统计窄条（`.tb-stat`，阻塞红/已完成绿）、工具栏、五列看板（固定高度 + 列内滚动）、卡片（编号 + 优先级圆点 `p0..p3` + 标题 + 类型标签 + 首字头像 + 截止日期，逾期红色）
  - [x] SubTask 4.2: 新增 `features/console/components/task-board.tsx` 导出 `TaskBoard`：查询 `listBoardItems()` 与当前用户；按五列渲染卡片；列标题含计数，列名高亮阻塞/已完成
  - [x] SubTask 4.3: 实现同列拖拽排序（沿用 `moveBoardItem`，跨列提示「请进入任务工作区执行状态操作。」）
  - [x] SubTask 4.4: 实现卡片标题跳转：角色优先（`PRODUCT`→详情、`UX`→`/ux`、`DEVELOPER`→`/dev`、`QA`→`/qa`），无匹配角色时按任务类型兜底（`UX_TASK`→`/ux`、`DEV_TASK`→`/dev`、`QA_TASK`→`/qa`、`BUG`→`/dev`）；无 `requirementId` 时渲染纯文本
  - [x] SubTask 4.5: `views/task-board-view.tsx` 改为渲染 `TaskBoard`；`features/console/index.ts` 导出 `TaskBoard`；`delivery-screen.tsx` 删除 `Tasks()` 与 `DeliveryKind` 的 `"tasks"`；删除 `console.module.css` 中已无引用的看板样式（`.board`/`.column` 及其子选择器/`.avatar`），删除前用 Grep 确认无其他引用
  - [x] SubTask 4.6: 更新 `console-screens.test.tsx`：移除两条依赖 `kind="tasks"` 的看板用例（断言迁移到 TaskBoard 测试），保留 QA 用例

- [x] Task 5: forge-web 实现三个筛选与统计联动
  - [x] SubTask 5.1: 执行人下拉：选项来自 `listRequirementMembers()`（`GET /v1/requirements/members`），`showSearch` 支持姓名/邮箱过滤，默认选中当前登录用户，附「全部」选项
  - [x] SubTask 5.2: 需求下拉：使用 `listBoardRequirements(assigneeUserId)`，`assigneeUserId` 为「全部」时不传参；选项为「全部」+ 接口结果；执行人变化时重置为「全部」
  - [x] SubTask 5.3: 优先级下拉：`URGENT/HIGH/MEDIUM/LOW`，文案复用 `priorityLabel`，默认「全部」
  - [x] SubTask 5.4: 过滤逻辑：按执行人、需求 `requirementId`、优先级过滤看板数据；统计窄条与列计数均基于过滤后集合；需求选项请求失败时下拉仅保留「全部」并提示错误
  - [x] SubTask 5.5: 看板/成员/选项加载与失败态：加载中显示 `Spin`，失败显示 `Alert` + `formatRequestError`

- [x] Task 6: forge-web 测试与质量门禁
  - [x] SubTask 6.1: 新增 `features/console/components/task-board.test.tsx`：默认执行人为当前用户；切换执行人后按新 `assigneeUserId` 请求需求选项且需求筛选重置为「全部」；优先级筛选生效；执行人+需求+优先级组合过滤后统计与列计数一致；卡片链接按角色命中与按任务类型兜底；无 `requirementId` 时无链接；五列标题为「待处理/进行中/阻塞中/待验证/已完成」且不含「评审中」
  - [x] SubTask 6.2: forge-server 运行新增与既有的 Requirement/Console 相关集成测试
  - [x] SubTask 6.3: 根目录运行 `make ci` 与 `git diff --check`，记录结果（含已存在的 pre-existing 失败项）

# Task Dependencies

- Task 3 depends on Task 1、Task 2（接口字段与路径确定后才能接入客户端）
- Task 4 depends on Task 3
- Task 5 depends on Task 4（筛选接入 TaskBoard 组件）
- Task 6 depends on Task 4、Task 5
- Task 1 与 Task 2 无耦合，可并行；Task 2.6 复用 Task 1.4 建立的测试类，需在 Task 1 之后执行
