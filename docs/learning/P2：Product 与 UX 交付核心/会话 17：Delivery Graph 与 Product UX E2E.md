# 会话 17：Delivery Graph 与 Product/UX E2E

## 本轮完成

- 新增从 Requirement 出发的 `GET /api/v1/work-items/{id}/delivery-graph`，统一返回 Work Item、文档、父子边和业务 Relation。
- 图遍历使用最短路径 BFS，环只访问一次；最大深度固定为 8，最大节点固定为 500，响应通过 `truncated` 明确提示不完整结果。
- 图数据由三条带 `workspace_id`、`project_id` 的 MyBatis 批量查询构成，不按节点回查数据库；当前 RBAC 模型下按 Work Item 类型和文档权限过滤节点，未授权节点也不会成为继续遍历的跳板。
- Requirement 页面增加 Delivery Graph 页签。小图使用 SVG 展示，同时始终提供可访问列表；超过 80 个节点时主动降级为列表，避免浏览器布局过载。
- 将 Product/UX 黄金回归延伸到 Delivery Graph：Requirement 材料、PRD 发布、Product Review、自动 UX Task、UX Task Review、UX Spec 发布、UX Review 和 `READY_FOR_DEV` 后，图中必须同时出现 Requirement、PRD、UX Task 与 UX Spec。

## 设计与调用链

调用链为：Web Requirement 页签 → Work Item API Client → `WorkItemController` → `DeliveryGraphQuery` → `PermissionEvaluator` 与 `DeliveryGraphStore` → 三条 scoped MyBatis SQL → 内存 BFS 与授权投影 → DTO → SVG/列表。

组合查询没有让模块 Repository 互相调用。Delivery Graph 拥有专用只读 Store，它只读取本查询需要的稳定字段；业务写模型仍由 Work Item、Document 等原服务负责。父子和 Relation 在遍历时按无向连通性发现节点，但响应边保留原始方向和语义。

## 数据与事务边界

MySQL 仍是 Work Item、文档和关系的唯一事实来源。图查询运行在只读事务中，不写缓存、不复制事实，也不调用 Agent、GitLab 或其他外部系统。三条查询均显式匹配 Workspace 与 Project，逻辑删除节点不会进入快照。

授权先确认根 Requirement 的 `requirement.read`，随后按项目内的资源类型能力决定节点是否可见。当前权限模型没有单 Work Item ACL，因此“逐节点保证”表现为每个节点都根据自己的类型套用对应项目权限；无权节点和相连边被删除，遍历不会穿过该节点泄露后继结构。

## 验证证据

- `DeliveryGraphQueryTest`：根节点空图、环、无权节点、500 节点截断、深度 8 截断全部通过。
- `RequirementTransitionIntegrationTest`：真实 Session、Controller、MyBatis、MySQL 链路通过；覆盖 scoped 图快照、环、错误 Project 404，以及完整 Product/UX 黄金前半程最终图投影。
- Web API/组件测试：请求 scope、SVG、列表和截断提示通过；TypeScript 与 ESLint 通过。
- 提交前执行 `make ci` 与 `git diff --check`，结果记录在本轮交付说明。

## 风险与遗留

- 所有带 JSON 正文的 `/api/v1/**` 成功响应现在统一包装为 `{code: 0, message: "success", data: ...}`；Web transport 在基础设施层透明解包。`204 No Content` 按 HTTP 规范继续没有正文，错误响应继续使用包含稳定错误码和 `requestId` 的 `ApiError`。
- 当前 Store 为避免 N+1，一次读取项目内全部 Work Item、Relation 和文档后再截断响应。对 MVP 项目简单可靠，但超大项目的数据库传输和 JVM 内存成本仍会增长；未来应在不削弱环检测与授权的前提下改为数据库递归查询或分层批量扩展。
- SVG 使用确定性分层布局，适合本轮 Product/UX 小图，不提供拖拽、缩放和复杂交叉边优化。超过 80 个节点直接使用列表；完整图交互属于独立体验增强，不应夹带进入本轮。
- 本轮只展示已存在的 Product/UX 事实；未接 GitLab、Development、QA、Release 或 Agent 节点，这些仍按后续会话推进。

## 3 个复盘问题

1. 为什么响应限制为 500 个节点，仍不能说明数据库查询成本已经被限制？
2. 为什么“过滤无权节点”之外，还必须禁止遍历穿过无权节点？
3. 为什么 Delivery Graph 应使用专用组合查询，而不能让 Work Item Repository 循环调用 Document Repository 和 Relation Repository？

响应截断只限制序列化与前端渲染；如果底层先读取全部项目事实，数据库传输和服务端内存仍随项目规模增长，因此两类边界必须分别度量。

穿过无权节点会泄露隐藏节点后方存在资产、关系深度甚至业务结构。Fail-closed 的图投影应把无权节点视为不可通行边界。

专用组合查询能显式声明 scope、选择最小字段并固定查询次数。跨 Repository 循环调用既容易形成 N+1，也会模糊模块写模型职责和事务边界。
