# 会话 14：Product 纵向切片

## 我完成了什么

- Project Overview 可以创建并打开 Requirement；详情页包含结构化材料、PRD、Product actions 和 Activity。
- Requirement 材料通过独立版本号保存；服务端返回 `availableActions` 与机器可读 `guardHints`。
- PRD 必须关联同范围 Requirement，正文以不可变版本保存；发布后才能批准产品评审并进入 `UX_IN_PROGRESS`。
- Developer、UX、QA 与 Release Approver 获得 PRD 只读权限，但没有 Product 写入或评审权限。

## 我理解的核心设计

页面提示不是授权事实。详情查询根据当前资源、身份、状态和 Guard 生成动作提示；动作提交后，服务端仍重新加载资源、校验项目范围与权限、执行 Guard，并以 `expectedVersion` 和幂等键提交。

调用链是：React Query 页面 → Session/CSRF REST API → Controller → Application Service → PermissionEvaluator/Guard → 带 workspace、project scope 的 MyBatis SQL → MySQL。页面刷新后重新查询服务端，不从浏览器状态推断业务状态。

Requirement、Requirement Details 与 Document 各有自己的乐观锁版本。状态转换、评审记录和 Activity 在一个本地事务内提交；文档版本保存与当前指针在一个事务，发布与 Outbox 在另一个事务。没有在持锁事务中调用外部系统。

## 失败路径

- 目标、纳入范围或验收标准缺失时，提交返回 `WORKFLOW_GUARD_FAILED` 和 `missing[]`。
- 没有关联且已发布的 PRD 时，批准返回 `missing: ["publishedPrd"]`。
- 陈旧的材料、文档或 Requirement 版本返回版本冲突；页面展示 API `requestId` 便于追踪。
- 隐藏或缺少按钮不构成安全边界；Developer 直接调用写 API 仍由服务端拒绝。

## 测试证据

- `RequirementWorkflowRegistryTest` 验证 Product 三个固定动作、目标状态、权限和非法组合。
- `RequirementTransitionIntegrationTest` 新增真实 HTTP happy path，并覆盖无发布 PRD 的 Guard、刷新后的服务端状态和评审记录；需要 Docker/Testcontainers。
- forge-web 已通过 ESLint、Feature 边界、TypeScript 与 18 个 Vitest 测试。
- Java 编译、测试编译、成员中文注释检查和领域定向单测通过。

## 遗留与风险

- PRD Tab 已复用会话 13 的 Tiptap/本地草稿能力；当前仅有 StarterKit 编辑面，显式表格工具栏仍需后续 UI 增强。
- `APPROVE_PRODUCT_REVIEW` 按本轮边界进入 UX 阶段，但 UX Task 的创建与评审属于会话 15，未提前实现。
- Product 评审的 `artifact_version_json` 尚未固化 PRD 版本快照；在会话 15 评审交付物模型统一时补齐。
- 本机若 Docker Socket 不可访问，Testcontainers 集成用例只能完成编译，不能执行数据库断言。

## 复盘问题

1. 为什么先完成纯人工闭环，再接入 Agent 生成 PRD？
2. 为什么 `availableActions` 只能改善体验，不能作为授权事实？
3. Product 纵向切片同时暴露了哪些按层开发不容易及时发现的契约问题？

1. 先完成人工闭环，才能先验证真正的业务事实：Requirement 材料是什么、PRD 何时算发布、哪些 Guard 必须满足、谁能批准、失败如何反馈。Agent 只是生成或辅助这些材料，不能替代状态机、权限和事务规则。否则容易出现“AI 生成了内容，但系统不知道内容是否可交付”的空壳流程。

2. availableActions 是服务端基于“当前用户、当前状态、当前材料”计算出的体验提示，但它会立刻过期：另一个人可能刚修改了材料或状态，权限也可能变化。客户端按钮可见不等于请求有权执行；真正提交时必须重新加载资源、校验 scope、权限、Guard、版本和幂等键。

3. 这次切片提前暴露了几类跨层问题：
  - 文档与 Requirement 原本没有真正关联，导致“已发布 PRD”无法成为可靠 Guard。
  - 前端需要顶层 Work Item 字段，而新增 hints 时若改成嵌套 DTO，会破坏已有 API 使用方。
  - Developer “只读”不是隐藏按钮，而是数据库权限种子、服务端授权和 PRD 查询能力都要一致。
  - MyBatis 返回 LocalDateTime，旧文档映射假设 Timestamp，只有真实 HTTP + MySQL 路径才暴露。
  - 文档当前版本与版本历史有双向外键，测试数据清理顺序必须显式处理。
  - 应用层直接依赖 Mapper 会违反架构边界，必须通过 Store port 隔离基础设施。
