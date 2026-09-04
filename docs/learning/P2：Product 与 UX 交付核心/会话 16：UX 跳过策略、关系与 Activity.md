# 会话 16：UX 跳过策略、关系与 Activity

## 本轮完成

- Project 创建时同步生成默认关闭的 Policy；只有 `ux.skip` 权限、Policy 开启、Requirement 具有 `BACKEND_ONLY`、`OPS` 或 `INTERNAL_TECH` 分类且 reason 非空时，才能从 `PRODUCT_REVIEW` 跳到 `READY_FOR_DEV`。
- Project Policy 提供带 `expectedVersion` 的读写 API；跳过分类通过受 Work Item scope 与编辑权限保护的标签 API 写入。
- `parent_id` 继续只表达树状从属；`work_item_relations` 管理 `DEPENDS_ON`、`BLOCKS`、`RELATES_TO` 等非树状关系，并拒绝自环、重复和跨 Project 端点。
- 评论作为独立业务事实追加写；统一 Activity 将状态事件和未删除评论按发生时间合并，Web 的 Activity 页签展示该投影。
- V12 迁移为四张新表及所有字段写入中文 MySQL `COMMENT`，并为既有 Project 回填默认 Policy。

## 核心设计

Permission 回答“这个用户能否尝试做某类动作”，Policy 回答“这个项目当前是否允许该例外路径”，标签回答“这个 Requirement 是否属于允许例外的业务类别”。三者不能互相替代：Owner 虽有 `ux.skip`，在策略关闭或普通需求上仍会被 Guard 拒绝。

跳过调用链为：Controller 接收固定 `SKIP_UX` → Application Service 按 Workspace/Project 加载 Requirement → 校验 `ux.skip` → Registry 确认当前状态 → Guard 查询 Project Policy、标签与 reason → 同一 MySQL 事务乐观更新状态并追加 `work_item_events`。跳过不伪造 UX Review；审计证据是带操作者、原因、时间和幂等键的状态事件。

关系调用链为：加载 source → 校验 source 编辑权限 → 使用同一 Workspace/Project scope 加载 target → 拒绝 self/duplicate → 写关系。数据库唯一键兜住并发重复。`parent_id` 适合“Requirement 包含 UX Task”的唯一层级；Relation 适合依赖、阻断和一般关联，避免一列承担多种语义。

Activity 是读模型而非新的事实来源。状态仍以 `work_items` 为准，工作流历史来自 `work_item_events`，讨论来自 `comments`；查询层只合并投影，不复制或覆盖原事实。所有写入均只访问 MySQL，没有在持锁事务中调用外部系统。

## 失败路径

- 有 `ux.skip` 但 Policy 关闭：422，`missing` 包含 `projectPolicy.allowSkipUx`。
- Policy 开启但缺少允许分类：422，`missing` 包含 `eligibleSkipUxLabel`。
- reason 为空白：422，`missing` 包含 `reason`，状态与事件均不写入。
- self relation：400；同方向、同类型重复关系：409；target 不在请求 Project scope：统一 404。
- Policy 的 `expectedVersion` 过期：409，避免并发管理覆盖。

## 测试证据

- `RequirementTransitionIntegrationTest` 与 `RequirementWorkflowRegistryTest`：通过；覆盖 SKIP_UX 三重 Guard、原因审计、关系自环/重复/跨 Project、Activity 顺序及既有工作流回归。
- `ProductionJavaMemberCommentTest`：通过；新增 Java 成员均符合中文普通块注释规则。
- `FlywayMigrationIntegrationTest`：V12 成功迁移；四张表和全部字段均有数据库元数据注释。
- Web `typecheck`、ESLint 与 18 项 Vitest：通过。

## 风险与遗留

- 本轮分类是受控枚举，不是完整的通用标签管理系统；若未来开放任意标签，需要单独设计规范化、重命名与授权。
- Relation 当前提供创建与查询，完整 Delivery Graph 的深度/节点截断、可视化和遍历留给会话 17。
- Activity 当前合并工作流事件与评论；评论编辑/删除、关系变更事件及审计后台展示不在本轮范围。
- 用户 Activity 面向协作可读性；安全审计日志面向不可抵赖与管理员调查，两者不能合并成同一事实表。

## 复盘问题

1. 为什么用户拥有 `ux.skip` 权限，仍可能被 Project Policy 或 Requirement 分类拒绝？
2. `parent_id` 与 Relation 分别适合表达什么关系，为什么不应混用？
3. 为什么 `work_item_events`/`comments` 的 Activity 投影不能替代安全审计日志？

权限只是主体能力上限，Policy 和资源事实仍决定这次动作是否合法；这能避免高权限角色无意绕过项目约束。

`parent_id` 表达单一、稳定、可沿树遍历的从属；Relation 表达可多条并存的有向业务语义。混用会让删除、遍历和 Guard 的含义变得模糊。

Activity 可以为了产品体验筛选、合并和隐藏逻辑删除评论；安全审计必须覆盖更广的敏感操作，并满足更严格的保留与防篡改要求。因此两者用途和可信边界不同。
