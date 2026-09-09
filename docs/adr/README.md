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
| [ADR-015](ADR-015-single-organization-product-scope.md) | Accepted | 单组织产品模型与内部兼容 Scope |

## 何时新增 ADR

更换事实数据库、会话或向量库；引入消息队列或微服务；修改认证模式、核心建模、Agent Tool 安全边界、实时协议、SCM 或自托管拓扑时，必须先新增 ADR，再同步设计、契约与测试。
