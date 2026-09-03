# 会话 09：Workspace、Project 与成员范围

## 我完成了什么

- `forge-server` 新增 `projects` 与 `project_members` 迁移，Workspace 内的 Project Key 唯一；归档保留项目事实，不执行删除。
- 新增 Workspace/Project 查询与命令服务、scoped JDBC Store 和 Controller，覆盖 Workspace 创建、成员管理、Project 创建、查询、归档及成员管理。
- Project 查询统一携带 `workspace_id`，并在查询层同时验证有效 Workspace Member 与 Project Member；Workspace Owner 可管理所属 Workspace 的项目。
- 成员移除只标记关系为 `REMOVED`，后续访问重新校验关系，因此 Workspace 或 Project 成员移除都会立即撤销项目访问。
- `forge-web` 将原 Project 占位页替换为真实项目列表、创建、详情、归档和成员管理页面，并增加 Workspace 成员设置页。

## 我理解的核心设计

### Workspace Member 与 Project Member

- `workspace_members` 表示用户是否进入一个租户范围，是 Project 成员关系成立的前置条件。
- `project_members` 只表示用户能访问哪个项目，不表示项目内角色或操作权限；角色与权限矩阵留给会话 10。
- 当前阶段使用初始化阶段已有的全局 `OWNER` 作为管理边界。Project Member 不能因为拥有访问范围而获得成员管理或归档能力。

### 调用链

1. Web 组件通过统一 API 层发送请求，并由 CSRF transport 注入 Session Cookie 和 CSRF Header。
2. Controller 从当前 Session 取得 `AuthContext`，不信任 URL 或请求体中的用户身份。
3. Application Service 先验证 Workspace Owner、Workspace Member 或 Project Member 范围。
4. scoped Store 将 Workspace 条件下沉到 SQL；跨 Workspace 的已知 ID 按资源不存在处理。
5. MySQL 提交业务事实后，Controller 返回稳定的资源、204、404 或 409 响应。

## 数据与事务边界

- 创建 Project 与创建者的 `project_members` 关系在同一个本地事务中提交；任一步失败都不能留下半个 Project。
- 创建 Workspace 时同时写入 Workspace、创建者成员关系和临时 Owner 角色关系。
- 归档使用 `WHERE ... AND version = expectedVersion` 的原子更新；成功后递增版本并写入 `archived_at`。
- 成员移除不删除历史关系，只更新状态和时间；读取时必须同时满足 Workspace Member 与 Project Member 为 `ACTIVE`。
- 本轮没有外部系统调用，不在数据库锁或事务中调用 GitLab、Qdrant、LLM 等服务。

## 关键实现取舍

- Project Key 由数据库唯一约束和应用层稳定 `409 PROJECT_KEY_CONFLICT` 双重保证，避免并发下只依赖预检查。
- 无权访问与资源不存在统一返回 `404 RESOURCE_NOT_FOUND`，减少跨租户资源枚举。
- 归档不是删除：项目 ID、Key、成员历史和版本事实都保留，后续恢复策略可以另行设计。
- 成员添加只接受已存在且有效的本地账号；邀请、注册和账号激活不在本轮范围内。
- Web 先用 `/me` 返回的 Workspace 列表解析 slug，再请求项目资源；slug 只用于导航，不能替代服务端授权。

## 失败路径与风险

- 同一 Workspace 重复 Project Key：返回 409，原项目不受影响。
- 旧版本归档或已归档项目再次归档：返回 409 `VERSION_CONFLICT`。
- 跨 Workspace 读取或归档：返回 404，不泄露资源是否存在。
- 移除 Project Member 后，该用户下一次读取立即返回 404；移除 Workspace Member 后，即使 Project Member 仍存在，也返回 404。
- Owner 不能移除自己的 Workspace/Project 成员资格，返回 400 `VALIDATION_FAILED`，避免留下不可管理的 Workspace。
- 当前 Project/成员命令尚未接入通用 audit/outbox 写入链；该能力应在明确事务与事件方案后补齐。

## 测试证据

- `WorkspaceProjectScopeIntegrationTest` 使用真实 HTTP、Session、CSRF、MySQL、Redis 和 Qdrant，覆盖创建、重复 Key、归档保留、版本冲突、成员撤销、跨 Workspace 读写和 Owner 自移除。
- 新增自移除测试先验证到 500，再补充异常映射后验证为 400，未吞掉失败。
- 前端 API 与页面验证通过：Vitest 8 个文件、17 个用例，另通过 typecheck、ESLint、模块边界检查和生产构建。
- 最终 `make ci` 与 `git diff --check` 通过；后端 Surefire 20 份报告无 failure/error。

## 遗留边界

- 会话 10 负责默认角色种子、Project 级角色、PermissionEvaluator、权限矩阵和缓存失效。
- 用户邀请、账号创建、Workspace 切换体验和恢复归档不属于本轮。
- 审计、Outbox 与后续异步事件应在不改变当前 MySQL 事实来源的前提下补充。

## 3 个复盘问题

1. 为什么只在 Controller 检查 `workspace_id` 不够？
   因为其他入口、后台任务或未来 Controller 可能绕过同一检查；只有查询层强制携带租户条件，才能让所有调用路径默认 fail closed。
2. 为什么 404 比 403 更适合跨租户资源访问？
   404 不确认资源是否存在，能减少 ID 枚举和租户边界泄露；403 适合调用方已经明确知道资源属于其可见范围、但缺少具体操作权限的场景。
3. 为什么 Project Member 不能代替 Project Role？
   Member 解决“能否进入项目范围”，Role 解决“进入后能做什么”；把两者合并会导致普通读取成员获得归档、成员管理等超出范围的能力。
