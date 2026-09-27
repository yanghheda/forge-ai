# ADR-026：需求交付五模块的写权限按领域收敛

- 状态：Accepted
- 日期：2026-09-27
- 相关：ADR-021 的领域管理员权限继承、ADR-022 的需求执行人与审批分离、ADR-024 的产品验收、ADR-025 的发布归属研发

## 上下文

需求详情页把交付过程拆成五个模块：PRD 文档、UX 设计、开发、QA 测试、发布。产品要求是：五个模块所有
成员都可以阅读，但编辑权限只归该模块的领域成员——PRD 属产品、UX 属设计、开发属研发、测试属测试、
发布属研发；其他角色只读。

前端已经通过 `canEditModule(roles, module)` 实现了这套模块判定，且全局管理员与所有者不绕过限制。但后端
`role_permissions` 矩阵仍是按资源类型粗粒度授权，存在两处偏差：

1. 读取侧不全：`qa.read`、`bug.read`、`repo.read`、`release.read` 只授予了部分角色，其他角色在模块内
   看不到完整的测试、仓库与发布事实。
2. 写入侧过宽：`document.*` 同时覆盖 PRD 与 UX 交付文档，`task.*` 同时覆盖开发与测试任务，且 `OWNER`
   与 `ADMIN` 默认持有全部权限。结果是前端置为只读的角色实际仍能写入，而前端置为可写的角色（如测试在
   测试模块）反而缺少对应权限码，前后端契约不一致，越权统一被伪装成 `RESOURCE_NOT_FOUND`。

根因是资源类型没有与模块边界对齐：不拆权限码就无法表达「PRD 只归产品、UX Spec 只归设计、测试任务只
归测试」。

## 决策

1. 新增按模块拆分的权限码：`ux.document.create/edit/publish`、`development.document.create/edit/publish`、
   `qa.create`、`qa.edit`。`document.*` 从此只代表 PRD 文档；测试任务从 `task.*` 改用 `qa.*`。
2. 五个模块的读取权限对全部 10 个系统角色开放：补齐 `qa.read`、`bug.read`、`repo.read`、`release.read`。
3. 五个模块的写入权限只授予该模块的基础角色与领域管理员：
   - PRD 域：`requirement.edit`、`requirement.accept`、`document.create/edit/publish`
     → `PRODUCT`、`PRODUCT_ADMIN`；`requirement.approve` 仍是需求审核的负责人专属动作，只保留
     `PRODUCT_ADMIN`（ADR-022、ADR-024）
   - 设计域：`ux.create/edit`、`ux.document.create/edit/publish` → `UX`、`UX_ADMIN`；`ux.approve` 仍是
     UX 评审的负责人专属动作，只保留 `UX_ADMIN`
   - 研发域：`task.create/edit`、`development.submit`、`repo.write`、`development.document.create/edit/publish`、
     `bug.resolve` → `DEVELOPER`、`DEVELOPER_ADMIN`
   - 测试域：`qa.create/edit/manage/execute`、`bug.create/edit/verify` → `QA`、`QA_ADMIN`
   - 发布域：`release.manage/precheck/deploy` → `DEVELOPER`、`DEVELOPER_ADMIN`（沿用 ADR-025）
4. `OWNER` 与 `ADMIN` 收敛为按模块只读，收回其全部模块写权限（含此前持有的 `requirement.approve`、
   `ux.approve`、发布写权限），只保留成员治理（`member.read/manage`）、
   外部集成（`integration.manage`）、Agent 运行（`agent.run`）、高风险审批（`approval.decide`）与需求创建
   （`requirement.create`）等跨模块能力。`OWNER` 的授权行数由 39 降为 17。
5. 例外：`requirement.create` 不下发给领域成员收回。需求创建是需求列表与仪表盘的全局入口，前端未按模块
   门禁，保留 `OWNER`、`ADMIN`、`PRODUCT`、`PRODUCT_ADMIN` 可创建，避免前端可点、后端拒绝。
6. 应用服务按工作项/文档所属模块动态取权限，不再使用跨模块的通用码：
   - `DocumentService` 按文档类型映射权限（PRD→`document.*`，UX 交付文档→`ux.document.*`，技术设计→
     `development.document.*`）用于创建、保存、发布。
   - `WorkItemType.QA_TASK` 的权限资源由 `task` 改为 `qa`，使任务创建、编辑与读取统一走 `qa.*`。
   - `ConsoleService.move`（看板排序）按工作项类型取该模块的 `.edit` 权限，而不是 `task.edit`。
7. 通用 Tool（`update_task`、`advance_task`、`publish_document`）的契约权限只能是粗粒度前置门禁，
   真正写入授权由应用服务按任务类型模块权限与需求内执行人身份判定；`publish_document` 拆出 PRD 专用语义，
   UX 交付文档发布改用新 Tool `publish_ux_document`（契约权限 `ux.document.publish`）。

## 后果

- 前后端权限契约一致：前端 `canEditModule` 置为只读的角色，后端也会拒绝写入；反之亦然。
- 阅读全开放：所有角色都能在五个模块内查看 PRD、UX、开发、测试与发布事实，符合「所有人都可以阅读」。
- Agent Tool 安全边界随之变化：`AgentSkill` 的启动门槛（`PRODUCT`→`requirement.read`、`UX`→`ux.read`、
  `DEVELOPER`→`task.read`、`QA`→`qa.read`、`RELEASE`→`release.read`）因读取权限全开放而实际上对所有角色
  可达。真正的写入仍由 Tool 契约权限与应用服务判定二次收口，因此这不构成越权，但技能可见面变宽是本次
  决策的直接后果。
- 领域管理员的权限是创建时点快照（ADR-021），收回基础角色权限时必须逐角色显式同步收回，迁移 V11 的
  `DELETE` 已按白名单逐角色处理，只影响系统角色，不触碰公司自定义角色。
- 迁移 V11 只调整权限码与 `role_permissions`，不改变表结构，也不修改历史迁移脚本。
- `make ci` 中的契约校验、forge-server 测试与 forge-web 测试需要同步更新权限矩阵断言。

## 替代方案

- 只改前端、保留后端粗粒度写权限：拒绝，前后端契约不一致会持续产生「前端可点、后端拒绝」或「前端只读、
  后端可写」的错配，掩盖真实授权边界。
- 在应用层用角色名硬编码判定而不拆权限码：拒绝，权限矩阵是事实来源，硬编码角色名会绕过 `role_permissions`
  的可审查性，且公司自定义角色无法继承。
- 把 `OWNER`/`ADMIN` 保留为模块全量写权限：拒绝，与前端 `canEditModule` 明确「全局管理员与所有者不绕过
  模块限制」的语义冲突，也会让五模块职责边界形同虚设。
- 让 `requirement.create` 一并按模块收回：拒绝，需求创建发生在列表页与仪表盘，前端未做模块门禁，收回会让
  入口按钮失效。
