# ADR-024：需求验收由产品领域成员执行

- 状态：Accepted
- 日期：2026-09-26
- 依据：用户确认验收职责归完成交付物的产品执行人，产品负责人只负责审核本领域执行人的动作，不再独占验收。
- 相关：修正 ADR-022 第 6 条「验收由产品负责人确认后进入待发布」；不改变 ADR-021 的审核码归属，`requirement.approve` 仍只授予 `ADMIN`、`OWNER`、`PRODUCT_ADMIN`；不改变 ADR-023 第 1 条「验收不开放给 Agent」。

## 范围与决策

本次只调整「验收中 → 待发布」这一步的操作主体与对应权限码，需求状态机、确定性 Guard、事实来源与部署拓扑均不变。

1. 验收主体由产品负责人扩展为产品领域成员：产品负责人或该需求已关联的产品执行人都可执行 `ACCEPT_REQUIREMENT`。这与 `QA_PASS`、`SUBMIT_UX_REVIEW`、`SUBMIT_FOR_QA` 保持一致——阶段结论由完成本阶段工作的一侧给出，领域负责人保留审核职责而非独占结论权。
2. 新增独立权限码 `requirement.accept`（resource=`requirement`，action=`accept`）。验收是产品领域成员对交付物是否满足验收标准的确认，与 `requirement.approve` 定义的领域管理员审核不是同一职责，因此不复用也不放宽审核码。
3. `ACCEPT_REQUIREMENT` 的权限码由 `requirement.approve` 改为 `requirement.accept`，授予 `PRODUCT`、`PRODUCT_ADMIN`、`ADMIN`、`OWNER`。`PRODUCT_ADMIN` 必须显式补授：它只在 V7 时点继承 `PRODUCT` 当时的权限集合，不自动获得后续新增的权限码。
4. 验收的确定性 Guard 不变：仍要求无未完成缺陷、无未完成 QA 任务；`APPROVE_REQUIREMENT` / `REJECT_REQUIREMENT` 的需求审核仍为产品负责人专属。

## 一致性与数据库

新增一条只会前进的 Flyway 迁移，用 `code` 关联写入 `permissions` 与 `role_permissions`，不硬编码自增 id，也不回改 V7。`forge-web` 无需改动：验收按钮继续由服务端按当前用户计算的 `availableActions` 驱动。Web、REST 与 Agent Tool 复用同一授权与状态规则，本次不新增或放宽任何 Agent 动作。

## 验证要求

覆盖：产品执行人可验收、产品负责人可验收、无该需求产品职责的其他领域成员被拒；`requirement.approve` 的持有角色保持 `ADMIN`、`OWNER`、`PRODUCT_ADMIN`；迁移后 `requirement.accept` 的持有角色为 `ADMIN`、`OWNER`、`PRODUCT`、`PRODUCT_ADMIN`；Agent 仍不能请求验收。
