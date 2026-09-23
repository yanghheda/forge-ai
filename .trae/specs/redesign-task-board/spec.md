# 任务看板改造 Spec

## Why

现有任务看板仅按旧设计渲染四列极简卡片（`DeliveryScreen kind="tasks"`），缺少执行人/需求/优先级筛选，卡片信息不足以支撑按人、按需求聚焦当前迭代工作。需要按设计稿重做看板主体样式，并补齐三个筛选与「点击卡片直达需求对应阶段」的导航能力。

## What Changes

- 看板改为独立页面组件，样式对齐设计稿 `docs/design/forgeai-console/pages/task-board.html`：页面标题区、顶部统计窄条、五列看板、固定列高与列内滚动、卡片（编号 + 优先级圆点 + 标题 + 类型标签 + 执行人首字头像 + 截止日期）。
- **BREAKING**（仅前端内部）：`/task-board` 不再经 `DeliveryScreen kind="tasks"` 渲染，改为 `TaskBoard` 组件；`DeliveryKind` 移除 `"tasks"`。
- 五列严格使用当前 Work Item 真实状态：待处理 `TODO`、进行中 `IN_PROGRESS`、阻塞中 `BLOCKED`、待验证 `WAITING_VERIFICATION`、已完成 `DONE`；**不采用设计稿中的「评审中」列**。
- 新增三个筛选：执行人（姓名可搜索，默认当前登录用户）、按需求（与执行人联动，默认「全部」）、优先级（默认「全部」）。
- 任务卡片点击跳转到需求详情，并按「角色优先 + 任务类型兜底」切换到对应阶段 tab。
- 保留既有「同列拖拽排序」能力（`PUT /v1/task-board/items/{id}/position` 与同列 Guard 语义不变）。
- 本次不做：看板/列表视图切换、迭代选择器、导出、新建任务、底部「流转规则说明」卡片。

## Impact

- Affected specs: 任务看板（console 模块）、需求协作人查询（workitem 读取侧）、forge-web 控制台导航。
- Affected code:
  - forge-server：`console/application/ConsoleModels.java`、`ConsoleStore.java`、`ConsoleService.java`、`console/infrastructure/persistence/ConsoleMapper.java`、`console/controller/ConsoleController.java`
  - forge-web：`features/console/api/console-api.ts`、`features/console/components/task-board.tsx`（新增）、`task-board.module.css`（新增）、`delivery-screen.tsx`、`console.module.css`、`features/console/index.ts`、`views/task-board-view.tsx`、`features/console/components/console-screens.test.tsx`、`features/console/components/task-board.test.tsx`（新增）
- 事实来源不变：看板仍由 `forge-server` 独占查询与写入；MySQL 为唯一事实来源；不新增表、不新增 Flyway 迁移。

## ADDED Requirements

### Requirement: 看板页面结构与设计稿样式

任务看板页面 SHALL 渲染页面标题区（「任务看板」+ 说明）、顶部统计窄条、五列看板主体；看板 SHALL 固定容器高度并使列内列表独立滚动。

#### Scenario: 打开看板

- **WHEN** 用户访问 `/task-board`
- **THEN** 页面显示「任务看板」标题与说明
- **AND** 顶部统计窄条展示五列计数
- **AND** 看板渲染五列，列高固定且列内卡片列表可独立滚动

### Requirement: 五列使用当前真实任务状态

看板列 SHALL 按现有状态枚举渲染且顺序固定为：待处理 `TODO`、进行中 `IN_PROGRESS`、阻塞中 `BLOCKED`、待验证 `WAITING_VERIFICATION`、已完成 `DONE`；阻塞列标题使用警示色、已完成列标题使用成功色。

#### Scenario: 列标题与状态一致

- **WHEN** 看板渲染完成
- **THEN** 五列标题依次为「待处理 / 进行中 / 阻塞中 / 待验证 / 已完成」
- **AND** 不出现设计稿中的「评审中」列

#### Scenario: 列计数与列内卡片一致

- **WHEN** 某列包含 N 张卡片
- **THEN** 该列标题计数与统计窄条中同一状态的计数均为 N

### Requirement: 看板卡片字段完整

每张卡片 SHALL 展示：工作项编号（`itemKey`）、优先级圆点、标题、类型标签（设计/开发/测试/缺陷）、执行人姓名首字头像、截止日期（`MM-DD`，逾期使用错误色）。

#### Scenario: 卡片展示真实字段

- **WHEN** 任务具备优先级、执行人与截止日期
- **THEN** 卡片优先级圆点颜色按 `URGENT/HIGH/MEDIUM/LOW` 区分
- **AND** 卡片展示执行人姓名首字与截止日期（`MM-DD`）
- **AND** 截止日期早于当日时使用错误色

### Requirement: 看板接口补充卡片所需字段

`GET /api/v1/task-board` SHALL 在既有字段之外返回：优先级 `priority`、截止日期 `dueAt`、执行人姓名 `assigneeName`、阻塞原因 `blockedReason`；查询 SHALL 继续限定 `organization_id`、`deleted_at IS NULL` 与任务类型（`UX_TASK`/`DEV_TASK`/`QA_TASK`/`BUG`）。

#### Scenario: 返回卡片字段

- **WHEN** 公司内存在带优先级与截止日期的任务
- **THEN** 接口返回该任务的 `priority`、`dueAt`、`assigneeName`
- **AND** 未分配执行人时 `assigneeName` 为空且不报错

### Requirement: 按执行人筛选

工具栏 SHALL 提供执行人下拉：选项来自当前公司有效成员（复用 `GET /api/v1/requirements/members`），支持按姓名或邮箱搜索，默认选中当前登录用户；SHALL 额外提供「全部」选项表示不按执行人过滤。

#### Scenario: 默认自己

- **WHEN** 用户首次打开任务看板
- **THEN** 执行人下拉默认选中当前登录用户
- **AND** 看板仅展示该用户的任务

#### Scenario: 搜索选择他人

- **WHEN** 用户在下拉中输入姓名片段并选择某成员
- **THEN** 看板仅展示该成员 `assigneeUserId` 的任务

#### Scenario: 选择全部执行人

- **WHEN** 用户选择「全部」
- **THEN** 看板展示公司全部任务，不再按执行人过滤

### Requirement: 按需求筛选并与执行人联动

工具栏 SHALL 提供需求下拉，选项来自 `GET /api/v1/task-board/requirements?assigneeUserId=`，即该执行人作为需求协作人（`requirement_participants` 或 `requirement_executors`）关联的需求；默认选项为「全部」表示不按需求过滤；执行人变化时 SHALL 重新加载选项并重置为「全部」。

#### Scenario: 选择执行人后联动

- **WHEN** 用户将执行人从自己切换为成员 B
- **THEN** 需求下拉选项刷新为成员 B 关联的需求集合
- **AND** 需求筛选重置为「全部」

#### Scenario: 选择具体需求

- **WHEN** 用户在需求下拉中选择某个需求
- **THEN** 看板仅展示 `requirementId` 等于该需求的任务

#### Scenario: 接口失败时降级

- **WHEN** 需求选项请求失败
- **THEN** 需求下拉仅保留「全部」并提示错误，看板可继续使用

### Requirement: 按优先级筛选

工具栏 SHALL 提供优先级下拉，选项为 `URGENT/HIGH/MEDIUM/LOW`（中文文案复用 `priorityLabel`）与默认「全部」。

#### Scenario: 选择优先级

- **WHEN** 用户选择「高」
- **THEN** 看板仅展示 `priority = HIGH` 的任务

#### Scenario: 组合筛选

- **WHEN** 用户同时设置执行人、需求与优先级
- **THEN** 看板仅展示同时满足三个条件的任务
- **AND** 统计窄条与各列计数基于过滤后的集合计算

### Requirement: 卡片点击跳转到需求对应阶段

卡片标题 SHALL 可点击；跳转目标 SHALL 按「角色优先、任务类型兜底」确定：当前账号角色为 `PRODUCT` → `/requirements/{id}`、`UX` → `/requirements/{id}/ux`、`DEVELOPER` → `/requirements/{id}/dev`、`QA` → `/requirements/{id}/qa`；账号仅有 `OWNER`/`ADMIN`/`RELEASE_APPROVER` 或无匹配角色时，按任务类型兜底：`UX_TASK` → `/ux`、`DEV_TASK` → `/dev`、`QA_TASK` → `/qa`、`BUG` → `/dev`。任务无 `requirementId` 时 SHALL 保持纯文本不可跳转。

#### Scenario: 角色命中

- **WHEN** 当前账号角色包含 `DEVELOPER`，用户点击任一有需求归属的任务
- **THEN** 跳转到 `/requirements/{id}/dev`，且阶段 tab 高亮「开发」

#### Scenario: 角色兜底

- **WHEN** 当前账号角色仅包含 `ADMIN`，用户点击 `QA_TASK` 任务
- **THEN** 跳转到 `/requirements/{id}/qa`

#### Scenario: 无需求归属

- **WHEN** 任务的 `requirementId` 为空
- **THEN** 卡片标题为纯文本，不产生跳转

## MODIFIED Requirements

### Requirement: 看板同列拖拽排序

看板 SHALL 保留既有拖拽排序能力：仅允许在同一列内拖动并调用 `PUT /v1/task-board/items/{id}/position`；跨列拖动 SHALL 被前端拒绝并提示「请进入任务工作区执行状态操作」。看板视觉改为新样式模块后，该行为与后端 Guard 语义 SHALL 保持不变。

#### Scenario: 同列拖拽

- **WHEN** 用户在同一列内拖动卡片到新位置
- **THEN** 调用位置接口并刷新看板

#### Scenario: 跨列拖拽

- **WHEN** 用户把卡片拖到其他列
- **THEN** 不调用接口并提示「请进入任务工作区执行状态操作。」

## REMOVED Requirements

### Requirement: 旧版看板渲染入口

**Reason**: 看板从 `DeliveryScreen` 的 `tasks` 分支剥离为独立页面组件，旧四列极简样式与 `console.module.css` 中看板样式不再使用。
**Migration**: `/task-board` 改由 `TaskBoard` 组件渲染；`DeliveryKind` 移除 `"tasks"`；相关断言迁移到 `task-board.test.tsx`。
