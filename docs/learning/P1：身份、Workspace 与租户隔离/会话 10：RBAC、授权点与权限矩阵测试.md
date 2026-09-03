# 会话 10：RBAC、授权点与权限矩阵测试

## 我完成了什么

- 新增 V6 RBAC 前进迁移：`permissions`、`role_permissions`，以及 `OWNER`、`ADMIN`、`PRODUCT`、`UX`、`DEVELOPER`、`QA`、`RELEASE_APPROVER` 七个系统角色种子。
- 为当前已有的 Workspace、成员和 Project API 建立最小权限集：`workspace.read/manage`、`member.read/manage`、`project.read/manage`。`OWNER` 和 `ADMIN` 拥有该最小集；其余默认角色拥有 Workspace/Project 读取权限。
- 新增 `PermissionEvaluator`、`MybatisPermissionStore` 和显式 `PermissionMapper`。授权过程先确认资源属于请求 Workspace、用户是有效 Workspace Member、用户具有 Project Member 或 Owner/Admin 的项目范围，再判断聚合后的角色权限；任何一步不满足即拒绝。
- 将 Workspace 成员读取/变更、Project 创建/归档/成员管理、Project 查询从硬编码 `OWNER` 判断切换为应用服务内的权限判断。
- Web 依据 `/me` 返回的 Workspace 角色隐藏 Project 创建、归档和成员管理按钮。此逻辑仅改善体验，不能作为安全控制。

## 我理解的核心设计

### RBAC 的五个元素

- Subject：当前 Session 中由 `AuthContext.userId` 证明的用户，而不是 URL 或请求体传入的用户标识。
- Role：系统预置角色；同一用户可以在 Workspace 或单个 Project 范围拥有多个角色。
- Permission：`resource.action` 形式的原子能力，例如 `project.manage`。
- Resource：当前实际加载的 Workspace 或 Project，必须在 SQL 与应用服务中确认归属。
- Action：读取、管理等被 Controller 入口和 Application Service 最终检查的操作。

### 调用链

1. 浏览器通过统一 API transport 携带 Cookie Session 与 CSRF Header 发起请求。
2. Controller 从 `AuthContext` 取得主体，不信任客户端声称的用户身份。
3. Command/Query Service 调用 `PermissionEvaluator` 做最终授权；因此将来增加内部入口时仍有一层业务授权保护。
4. `MybatisPermissionStore` 通过 `PermissionMapper` 中可审查的 scope SQL 读取有效成员关系、角色分配、角色权限和资源所属范围，并只聚合 Workspace 级与当前 Project 级角色。
5. 无权限、跨租户或资源不存在均返回 `RESOURCE_NOT_FOUND`；成功后才执行已有的本地业务事务。

### 关键不变量与事务边界

- `workspace_members` 解决“是否进入租户”，`project_members` 解决“是否进入项目”，`member_roles` 与 `role_permissions` 解决“可做什么”；三者不能互相替代。
- 权限事实源是 MySQL。当前实现每次回源读取，因此 Redis 不可用不会把拒绝错误地变成允许。
- 创建 Project 和创建者 Project Member 仍在同一事务；归档仍采用乐观锁更新。授权查询不修改状态，也不在事务中调用外部系统。
- `OWNER` 拥有默认权限并不代表能绕过未来的 HIGH 审批；HIGH 风险策略、审批人隔离和 Agent Tool 重验属于后续会话。

### 为什么没有只在前端或 Controller 做权限判断

- 前端按钮可由浏览器开发工具重新显示，不能建立安全边界。
- Controller 是 HTTP 入口之一；后续内部 Tool、后台任务或新的 Controller 都可能调用应用服务。最终授权放在应用服务可避免“换入口即绕过”。
- 仅按角色代码检查会遗漏资源范围：某用户即使是其他 Workspace 的 Admin，也不能管理当前 Workspace 的 Project。

## 失败路径与风险

- 用户不是有效 Workspace Member：权限集合为空，返回 404。
- Project ID 属于另一 Workspace，或不存在：Project scope 加载失败，返回 404，不泄露 ID 是否存在。
- 仅有 Project Member、没有角色权限：无法读取 Project；测试中必须显式授予 `PRODUCT`，证明 Member 与 Role 的边界。
- 迁移后旧初始化流程若仍尝试插入 `OWNER` 会造成重复角色；实现已改为查询并绑定迁移种子角色。
- 当前实现尚未引入 `membershipVersion` Redis 缓存和正式角色分配 API；因此正确性来自实时 MySQL 回源，而不是缓存失效机制。该缺口不能被 UI 隐藏或 mock 掩盖。

## 测试证据

- `FlywayMigrationIntegrationTest.rbacMigrationSeedsDefaultRolesAndCorePermissions`：验证 V6、7 个系统角色、6 个核心权限及 Owner 的全量映射。
- `WorkspaceProjectScopeIntegrationTest.projectMemberRemovalAndWorkspaceMemberRemovalBothInvalidateProjectAccess`：为测试用户显式授予 `PRODUCT` 后可读；移除 Project Member 或 Workspace Member 后立即 404。
- `./mvnw -q test`：后端全量测试通过，包含真实 MySQL、Redis、Qdrant Testcontainers 集成链路。
- `npm test -- --run` 和 `npm run lint`：前端 8 个测试文件、17 个用例及 lint 通过。
- 这些测试尚不能证明：Redis 版本化权限缓存的失效正确性、所有默认角色的完整业务权限矩阵、HIGH 审批与 Agent Tool 的二次授权；这些必须在对应能力落地时增加真实集成测试。

## 仍不清楚的问题

- 成员角色变更 API 采用“替换整组角色”还是“增删单一角色”更利于审计、并发控制和回滚？
- Redis 缓存键应如何将 Workspace Member 版本、Project Member 版本和角色分配变更统一纳入失效条件？
- 当一个用户同时拥有 Workspace 级 `PRODUCT` 与 Project 级 `DEVELOPER` 时，`/me`、Project DTO 与前端按钮应如何安全地呈现有效权限？

## 3 个复盘问题

1. 为什么 Project Member 不能天然等同于 `project.read`？
   因为 Member 只证明资源范围；若自动授予读取或管理能力，角色配置就会失去最小权限意义，且难以表达未来的受限成员。
2. 为什么无权访问统一返回 404，而不是 403？
   对跨租户和已知 ID 请求，404 不确认资源是否存在，能降低枚举风险；系统内部日志仍可基于 requestId 区分拒绝原因。
3. 为什么缓存故障必须回源而不能默认允许？
   缓存是可丢失的派生状态，不是权限事实源。允许缓存故障放行会把基础设施故障扩大为越权漏洞；回源失败应保持 fail closed。
