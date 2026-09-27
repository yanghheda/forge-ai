# ADR-025：发布写权限归属研发领域

- 状态：Accepted
- 日期：2026-09-26
- 相关：ADR-021 的领域管理员权限继承、ADR-019 的 `deploy_release` HIGH 审批与职责分离

## 上下文

`release.manage`、`release.precheck` 原归属 `PRODUCT`、`QA` 及其领域管理员，`release.deploy` 只归属
`PRODUCT`，而 `DEVELOPER` 仅持有 `release.read`。但发布候选版本聚合的 CI、QA、Bug 证据与 Release Note
由研发产出，研发才是发布准备的实际执行方；产品与测试只在验收结论与质量结论上提供输入。

同时前端发布工作区已把发布模块的可编辑角色判定为 `DEVELOPER`、`DEVELOPER_ADMIN`，后端矩阵未同步，造成
前后端权限契约不一致：研发能看到「创建发布候选版本」却被服务端以权限不足拒绝（统一伪装为
`RESOURCE_NOT_FOUND`），产品与测试则被前端置为只读却实际持有写权限。

## 决策

1. `release.manage`、`release.precheck`、`release.deploy` 授予 `DEVELOPER` 与 `DEVELOPER_ADMIN`。
2. 从 `PRODUCT`、`QA`、`PRODUCT_ADMIN`、`QA_ADMIN` 收回上述三个权限码，只保留 `release.read`。
3. `OWNER` 与 `ADMIN` 保持全量权限语义不变，本次不新增权限码。
4. `approval.decide` 归属不变（`OWNER`、`ADMIN`、`DEVELOPER_ADMIN`）；`deploy_release` 仍为 HIGH 风险
   Tool，必须由非发起人审批，因此开发发起模拟部署时仍需要他人决定。
5. 前端 `canEditModule` 的 `release` 模块判定已与本次矩阵一致，不需要改动。

## 后果

- 研发可完整走通「创建候选版本 → 运行确定性预检 → 申请 HIGH 审批并模拟部署」。
- 产品与测试不再写发布，只能读取 Release Candidate 与 Precheck 快照。
- Agent Tool 安全边界随之变化：研发身份的 Agent Run 现在可以通过 `create_release`、`update_release_note`、
  `run_release_precheck`、`deploy_release` 写入；产品与测试身份的 Run 会被拒绝。
- 发布审批人判定（`ReleaseMapper.approverAvailable`）依赖 `OWNER`、`ADMIN`、`DEVELOPER_ADMIN`，不受影响；
  `OWNER` 的授权行数保持 39 不变。
- 迁移 V10 只调整 `role_permissions`，不改变表结构，也不修改历史迁移脚本。
- 领域管理员的权限是创建时点快照，收回基础角色权限时必须显式同步收回对应管理员，否则会出现只改了一半的
  权限矩阵。

## 替代方案

- 只改前端、保留后端产品与测试的写权限：拒绝，发布材料由研发负责，产品持有写权限会造成权责不符，且掩盖
  前后端契约不一致的真实原因。
- 把 `release.deploy` 留在 `PRODUCT`：拒绝，创建与部署申请应归属同一责任人，拆开会让发布单由非交付方推进。
- 只授予 `DEVELOPER_ADMIN` 而不授予 `DEVELOPER`：拒绝，发布准备是研发成员的常规工作，不应只由领域负责人操作。
