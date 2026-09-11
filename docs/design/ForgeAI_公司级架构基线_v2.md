# ForgeAI 公司级架构基线 v2

## 产品定位

ForgeAI 是供单家公司私有化部署的内部 AI 软件交付平台。一个部署实例只服务一家公司；开源使用者通过部署独立实例完成公司间隔离。公司下直接创建需求、任务、文档、Agent Run、测试和发布，不设置中间业务容器。

## 唯一作用域

- `organizations` 是唯一公司事实和业务作用域。
- 登录用户必须通过 `organization_members` 属于实例公司；成员状态为 `PENDING`、`ACTIVE` 或 `DISABLED`。
- 所有业务表直接保存 `organization_id`，对外 API 不接受作用域参数，而是从服务端 Session 解析。
- Work Item 使用公司级 `REQ-n` 原子序列。
- 权限、角色、策略、通知、搜索、统计、看板、Agent 会话和 GitLab 连接都属于公司。

## 应用边界

- `forge-server` 是业务事实、权限、状态机和事务的唯一权威。
- `forge-web` 只调用 Server API，不连接数据库、Agent Runtime 或 GitLab。
- `forge-agent` 使用短期 Run Token 和受控 Tool API，不持有业务数据库或 GitLab 凭据。
- MySQL 保存事实；Redis 保存可丢失会话/短期状态；Qdrant 保存可重建索引。

## 核心能力

1. 公司需求概览、我的需求、需求详情与全局搜索。
2. PRD、UX、开发、QA、发布的固定状态机与 Guard。
3. 公司全局任务看板、泳道排序和拖拽位置持久化。
4. Agent 连续会话、消息历史、Run、SSE Trace、审批和 Trace 导出。
5. 公司成员申请审核、启停、角色修改和邀请状态。
6. 公司级周交付量、活跃 Agent、个人待办、阶段分布和趋势统计。
7. 仅支持 GitLab 的连接、仓库绑定、分支、Merge Request、Pipeline 和 Webhook。

## 数据库演进

项目尚未上线，数据库历史已压平为 `V1__company_platform_baseline.sql`。新库直接创建当前公司级完整模型，不再创建或转换任何旧业务容器；基线发布后的结构变更从 V2 开始追加前进式迁移。

详细决策见 `ADR-016-remove-workspace-project-scope.md`。
