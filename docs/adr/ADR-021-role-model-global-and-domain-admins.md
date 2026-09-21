# ADR-021：角色模型调整为全局管理员与四个领域管理员

- 状态：Accepted
- 日期：2026-09-21
- 相关：ADR-019 中 `deploy_release` 的最终发布审批由本 ADR 重新指派审批角色

## 上下文

原系统角色集合为 `OWNER`、`ADMIN`、`PRODUCT`、`UX`、`DEVELOPER`、`QA`、`RELEASE_APPROVER`。`ADMIN`
与 `OWNER` 权限几乎一致，命名上无法体现“全局治理”与“领域负责”的区别；发布审批由独立的
`RELEASE_APPROVER` 承担，但该角色不承担开发交付责任，导致“谁能审批”与“谁负责交付”脱节。

后续交付流程要求：一个需求先关联产品、UX、开发、测试四个领域的负责人，再由各领域负责人把工作分配给本
领域成员、为成员创建阶段任务，并对本领域成员执行审批动作。这要求权限模型能表达“领域负责人”这一层，
且领域负责人本身也作为该领域的主负责人。

同时，需求状态机后续会追加“需求审核”和“UX 审核”两个阶段，由对应领域管理员决定，因此产品与 UX 领域
需要可被单独授予的审核权限码。

## 决策

1. 保留 `ADMIN` 角色代码不变，仅将展示名改为“全局管理员”。其权限集合与 `OWNER` 相同，作为仅次于 Owner
   的全局治理角色；差异仅在于 Owner 不可被分配、不可被降级或编辑。
2. 新增四个领域管理员系统角色：`PRODUCT_ADMIN`（产品管理员）、`UX_ADMIN`（UX 管理员）、
   `DEVELOPER_ADMIN`（开发管理员）、`QA_ADMIN`（测试管理员）。四者均为系统角色，只可由全局管理员或
   Owner 分配，不支持自助注册。
3. 领域管理员权限通过继承基础角色获得：`PRODUCT_ADMIN` 继承 `PRODUCT`、`UX_ADMIN` 继承 `UX`、
   `QA_ADMIN` 继承 `QA`；`DEVELOPER_ADMIN` 继承 `DEVELOPER` 并承接原 `RELEASE_APPROVER` 的权限。
4. 新增两个独立审核权限码：`requirement.approve` 与 `ux.approve`。本次只建码并授予角色——`OWNER` 与
   `ADMIN` 同时持有两者，`requirement.approve` 额外只授予 `PRODUCT_ADMIN`，`ux.approve` 额外只授予
   `UX_ADMIN`。普通 `PRODUCT`、`UX` 成员不持有审核码。需求状态机的接入在后续阶段单独实施。
5. 删除 `RELEASE_APPROVER` 系统角色及其 `member_roles`、`role_permissions` 关联。最终发布审批职责移交
   `DEVELOPER_ADMIN`、`ADMIN` 与 `OWNER`；`ReleaseMapper.approverAvailable` 的审批人判定同步改为这三个
   角色。
6. 领域管理员本次不参与成员治理（成员审核与角色分配仍限 `OWNER`、`ADMIN`），也不参与需求参与人分配；
   这两条边界在后续交付流程改造中另行决策。

## 后果

- 角色总数由 7 个变为 10 个，`OWNER` 的权限数量由 36 增加至 38（新增两个审核码）。
- 服务端授权仍只按权限码解析，新增角色的权限完全由 `role_permissions` 决定；任何新增角色若未同步授权，
  登录后将无业务权限。
- 前端 `roleLabels`、模块可编辑判定（`canEditModule`）、注册角色下拉与成员管理角色选项均需与新角色集合
  对齐：领域管理员只能编辑本领域模块，`OWNER`/`ADMIN` 不绕过模块限制。
- 注册可选角色收敛为 `ADMIN`、`PRODUCT`、`UX`、`DEVELOPER`、`QA` 五项；提交领域管理员代码将被校验拒绝。
- 发布审批人判定变更触及 Agent Tool 审批安全边界，故以本 ADR 记录，`ADR-019` 的 HIGH 审批与职责分离语义
  不变。

## 替代方案

- 新增 `GLOBAL_ADMIN` 代码替换 `ADMIN`：拒绝，因为角色代码变更会波及既有 `member_roles`、前端判定与
  历史数据，收益仅为命名一致。
- 让领域管理员直接复用基础角色并附加角色叠加实现“负责人”语义：拒绝，因为基础角色权限一致，无法在
  权限码层面区分“领域负责人”与“领域成员”，也无法承载后续领域审批。
- 保留 `RELEASE_APPROVER` 与开发管理员并存：拒绝，因为发布审批与开发交付责任应归属同一责任人，保留独立
  审批角色会继续造成权责脱节。
- 将 `requirement.approve` / `ux.approve` 直接授予 `PRODUCT` / `UX`：拒绝，因为审核必须由领域管理员执行，
  普通成员不应具备阶段审核权。
