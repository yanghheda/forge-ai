# 架构决策记录

ADR 用于记录影响多个模块、长期演进或安全边界的架构决策，重点解释决策背景、选择理由和后果，而不是复述代码。

## 状态

- `Proposed`：正在讨论，尚不能作为实现依据。
- `Accepted`：已经接受，是当前实现基线。
- `Deprecated`：不再推荐，但历史实现可能仍依赖。
- `Superseded`：已被另一份 ADR 明确替代。

已接受 ADR 的内容不得被静默改变。若结论发生变化，应新增 ADR，并在新旧记录中建立替代关系。

## 索引

| ADR | 状态 | 决策 |
|---|---|---|
| [ADR-001](ADR-001-modular-monolith.md) | Accepted | 模块化单体而非业务微服务 |
| [ADR-002](ADR-002-java-business-authority.md) | Accepted | Java 为业务权威，Python 为独立 Agent Service |
| [ADR-010](ADR-010-compose-first-self-hosting.md) | Accepted | Docker Compose 自托管优先 |
| [ADR-014](ADR-014-local-docker-desktop-mvp.md) | Accepted | 本地开发使用 Docker Desktop 运行依赖服务 |
| [ADR-011](ADR-011-openapi-contract-source.md) | Accepted | OpenAPI 为跨端 REST 契约来源 |
| [ADR-013](ADR-013-springdoc-swagger-ui.md) | Accepted | 使用 springdoc-openapi 与 Swagger UI |
| [ADR-015](ADR-015-single-organization-product-scope.md) | Superseded | 单组织产品模型与内部兼容 Scope |
| [ADR-016](ADR-016-remove-workspace-project-scope.md) | Accepted | 公司直属资源并物理移除 Workspace/Project scope |
| [ADR-017](ADR-017-stage-aware-conversational-agent.md) | Accepted | 阶段感知的角色会话驱动交付流程 |
| [ADR-018](ADR-018-agent-confirmation-and-high-risk-approval.md) | Accepted | 区分 Agent 代执行确认与 HIGH 风险审批 |
| [ADR-020](ADR-020-shared-server-infrastructure.md) | Accepted | 单机多项目共享服务器级基础设施（网络接入范围由 ADR-027 修订） |
| [ADR-021](ADR-021-role-model-global-and-domain-admins.md) | Accepted | 全局管理员与四个领域管理员的角色模型 |

| [ADR-022](ADR-022-requirement-leads-and-task-lifecycle.md) | Accepted | 需求负责人、执行人、四方会签与统一任务生命周期 |
| [ADR-023](ADR-023-agent-action-coverage-and-forced-confirmation.md) | Accepted | Agent 动作覆盖范围与强制人工确认 |
| [ADR-024](ADR-024-acceptance-by-product-domain-member.md) | Accepted | 需求验收由产品领域成员执行 |
| [ADR-025](ADR-025-release-ownership-in-developer-domain.md) | Accepted | 发布写权限归属研发领域 |
| [ADR-026](ADR-026-module-write-ownership-by-domain.md) | Accepted | 需求交付五模块写权限按领域收敛 |
| [ADR-027](ADR-027-production-edge-entry.md) | Accepted | 生产环境经共享边缘 nginx 以独立子域名接入 ForgeAI |
| [ADR-028](ADR-028-guest-demo-mode.md) | Accepted | 游客模式共享演示账号（跳过审核、持有四个领域基础角色与领域管理员角色） |

## 何时新增 ADR

更换事实数据库、会话或向量库；引入消息队列或微服务；修改认证模式、核心建模、Agent Tool 安全边界、实时协议、SCM 或自托管拓扑时，必须先新增 ADR，再同步设计、契约与测试。
