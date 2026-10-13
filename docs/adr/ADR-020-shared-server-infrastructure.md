# ADR-020：单机多项目共享服务器级基础设施

- 状态：Accepted
- 日期：2026-09-19
- 替代：ADR-014，并替代 ADR-010 中应用与数据服务同属一个项目 Compose 的拓扑细节
- 修订：网络接入范围由 ADR-027 修订——生产环境下 `forge-web` 为对接共享边缘 nginx 而加入 `infra-net`

## 上下文

当前只有一台服务器，后续多个个人项目都会部署在同一台服务器。若每个项目分别运行 MySQL、Redis
和 Qdrant，会重复占用内存、增加备份与升级次数，并使数据生命周期错误地绑定到某个业务项目。
服务器已在 `/opt/infra` 独立部署共享数据服务并创建 Docker 外部网络 `infra-net`。

共享服务实例不能等价为共享业务命名空间。不同项目若共用 MySQL schema、数据库账号、Redis 键前缀
或 Qdrant collection 名称，会产生迁移冲突、越权访问和误清理风险。

## 决策

- `/opt/infra` 的独立 Compose 是 MySQL、Redis、Qdrant 容器和持久卷的唯一部署来源；ForgeAI
  仓库不再声明、启动、停止或升级这三个服务。
- ForgeAI Compose 只包含 `forge-web`、`forge-server`、`forge-agent`。只有 `forge-server`
  加入外部网络 `infra-net`；Web 与 Agent 继续只通过 Server 访问业务数据能力。
  （`forge-web` 的网络接入范围已被 ADR-027 修订：生产环境为对接共享边缘 nginx 而加入
  `infra-net`，且不接收任何数据服务凭据；数据访问纪律与命名空间隔离结论不变。）
- 多个项目共享数据服务实例，但必须逻辑隔离：每个项目使用独立 MySQL database/user、唯一 Redis
  键前缀和唯一 Qdrant collection 前缀。ForgeAI 默认前缀为 `forge-ai` 和 `forge_ai_`。
- MySQL 仍是 ForgeAI 事实来源；Redis 仍是可丢失短期状态；Qdrant 仍是可重建派生索引。
- `infra-net` 与数据服务不得暴露公网端口。基础设施的凭据、备份、恢复、版本升级和故障维护由
  服务器级运维流程负责，不能由任一业务项目的 `down`、清理或升级命令触发。

## 后果

- 停止、重建或升级 ForgeAI 不会影响共享数据容器和其他项目。
- 单机资源利用率与基础设施维护成本下降，备份位置和运行版本统一。
- 共享实例成为同机多个项目的共同故障域；基础设施维护必须评估所有接入项目并统一安排窗口。
- 应用启动不再通过 Compose `depends_on` 等待数据服务，必须依靠健康检查、启动失败重试和运维顺序。
- MySQL database/user、Redis 前缀和 Qdrant collection 前缀属于部署必填隔离契约；新增项目时必须分配新值。
- 本仓库的端到端 Compose Smoke 需要预先存在的 `infra-net` 和专用测试命名空间。

## 替代方案

- 每个项目自带一套 MySQL、Redis、Qdrant：不采用，单机资源和运维成本重复，且数据生命周期与应用耦合。
- 所有项目共用同一 MySQL schema、账号和无前缀 Redis/Qdrant 数据：拒绝，无法形成可审查的隔离边界。
- 立即购买云托管数据库、缓存和向量库：暂不采用，当前单服务器阶段成本与复杂度收益不匹配。
- Kubernetes 统一编排所有项目：暂不采用，仍超出个人单机部署的维护目标。
