# ADR-016：公司直属资源并物理移除 Workspace/Project Scope

- 状态：Accepted
- 日期：2026-09-10
- 替代：[ADR-015](ADR-015-single-organization-product-scope.md)

## 上下文

ADR-015 只在产品界面隐藏 Workspace 与 Project，服务端、数据库和 Agent 契约仍以内部默认
`workspace_id/project_id` 作为兼容 scope。该兼容层持续渗透公开 API、授权、编号、文档、GitLab、
QA、发布与 Agent Run，使公司级产品模型仍需维护两套概念和大量作用域转换。

产品决策已经明确：一个 ForgeAI 实例只服务一家公司，公司下直接创建 Requirement；Workspace 和
Project 不再是产品或后端领域概念，也不需要兼容旧接口设计。

## 决策

1. `organizations` 是实例内唯一的业务范围和租户事实；所有用户、成员及业务资源直接归属该公司。
2. 删除 `workspaces`、`projects`、`project_members`、项目策略和项目编号序列。公司成员、角色与权限
   直接基于 `organization_id`。
3. 所有原 `workspace_id/project_id` 资源列统一迁移为 `organization_id`。公开 API 不接受任何 scope
   参数；服务端从当前 Session 解析公司，并在每次资源查询中显式携带 `organization_id`。
4. Work Item 使用公司级单调序列，展示编号固定为 `REQ-{number}`；Requirement、UX、Dev、QA、Bug
   都共享该序列。
5. GitLab 连接与仓库直接属于公司；仓库不再绑定 Project。Requirement 与开发任务通过 Work Item
   关系连接到 Branch、MR 和 Pipeline。
6. Agent Run、审批、Trace、文档检索、QA 与 Release 只携带 `organizationId` 和可选
   `workItemId/requirementId`。Agent Tool 仍由 Server 逐次鉴权，不因单公司模型降低风险控制。
7. 项目尚未上线且没有需要兼容的生产数据库，因此将历史迁移压平为公司级 V1 基线；从该基线发布后只允许新增前进式迁移。

## 数据迁移原则

- 新安装只执行 `V1__company_platform_baseline.sql`，直接创建公司级完整结构，不创建任何旧范围中间对象。
- V1 包含实例设置、系统角色、权限及角色权限等启动必需种子数据，业务事实表保持为空。
- 项目正式发布后 V1 视为不可变；后续数据库调整从 V2 开始，只允许新增前进式迁移。

## 后果

- Web、公开 API 与数据库使用同一公司级模型，不再存在默认 scope 或隐藏项目的转换代码。
- 授权查询仍必须显式限制 `organization_id`，单公司部署不能成为无范围查询的理由。
- 旧 `/workspaces`、`/projects` 以及带 `workspaceId/projectId` 的 API 会被移除，是一次有意的破坏性升级。
- 旧客户端和旧 Agent Runtime 必须同步升级；OpenAPI 是新契约的唯一来源。

## 替代方案

- 继续保留内部默认 Workspace/Project：拒绝，因为它维持了用户已明确取消的后端模型。
- 只移除 Controller 参数但保留数据库列：拒绝，因为隐式转换继续污染授权、查询和 Agent 契约。
- 以无 scope 查询代替公司范围：拒绝，因为会削弱资源隔离、审计和未来迁移能力。
