# ADR-015：单组织产品模型与内部兼容 Scope

- 状态：Accepted
- 日期：2026-09-09

## 上下文

ForgeAI 的现有产品入口要求用户依次理解并创建 Organization、Workspace 和 Project，才能开始创建 Requirement。对于部署在单个公司内网的研发平台，这个层级没有提供足够价值，反而增加了初始化、导航、成员管理和需求检索的成本。

现有数据库、授权 SQL、Agent Run、GitLab、QA 与 Release 已广泛使用 `workspace_id` 和 `project_id` 作为防御性 scope。立即物理删除这些列会把一次产品信息架构改版扩大为所有交付模块的高风险迁移。

## 决策

产品模型统一为一个初始化时创建的公司。一个 ForgeAI 实例只服务一家部署它的公司，用户界面和新公开 API 不再展示或要求选择 Workspace、Project；初始化后直接进入 Requirement 概览与创建流程。

`forge-server` 在初始化事务内为 Organization 创建唯一的内部默认 Workspace、默认 Project 和编号序列，并把它们记录为实例默认 scope。新公开 API 从当前 Session 和数据库中的默认 scope 解析资源范围，禁止客户端提交或覆盖 scope。

现有 `workspace_id`、`project_id`、旧路径与旧 API 暂时保留为兼容层，用于继续支撑现有 Agent、GitLab、QA、Release 和历史数据。它们不再属于新产品模型，也不得重新暴露为用户需要选择的概念。后续物理移除必须另建 ADR，并提供全链路数据迁移与回滚方案。

初始化只创建一个 `OWNER` 账号。初始化完成后，其他成员通过公开注册入口自行创建账号，并只能选择业务角色；`OWNER`、`ADMIN` 等管理角色不得自助获取。Requirement 使用独立参与人关系关联 `PRODUCT`、`UX`、`DEVELOPER`、`QA`，服务端验证参与人是当前 Organization 的有效成员且持有对应角色。

首个 Owner 依靠既有 Owner 权限执行全部工作流，不要求额外创建或切换角色。其他成员仍由角色权限矩阵约束各阶段操作。

## 后果

- 初始化字段、顶层路由、导航和 Requirement API 更简单，用户无需理解中间层级。
- 租户事实、授权与事务权威仍在 `forge-server`，Web 不能自行缓存或拼装默认 scope。
- 自助注册必须限制可选角色、保持邮箱唯一并在同一事务建立用户、成员和角色事实。
- “我的需求”以 Requirement 参与人关系为准；Owner 可查看全部需求并代行所有业务角色。
- 兼容表会暂时保留产品不可见的技术债务，需要在后续专门迁移中清理。

## 替代方案

- 继续暴露 Workspace 与 Project：拒绝，因为与公司内部直接按需求协作的流程冲突。
- 立即删除全部 scope 列：拒绝，因为会同时重写授权、Agent、SCM、QA 与发布链路，无法在本次改版内安全验证。
- 由前端保存默认 Workspace/Project：拒绝，因为客户端不是资源范围和授权事实来源。
