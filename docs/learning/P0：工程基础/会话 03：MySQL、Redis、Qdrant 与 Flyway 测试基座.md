# 会话 03：MySQL、Redis、Qdrant 与 Flyway 测试基座

## 我完成了什么

- 用户可见结果：建立 MySQL、Redis、Qdrant 的本机 Compose 配置，以及使用真实容器的 Server 集成测试基座。
- Flyway 可以从空 MySQL 数据库执行 `V1`、`V2`，创建带中文元数据注释的 `instance_settings` 单例表并插入唯一初始记录。
- 启动链路：Server 启动 → Spring 创建 DataSource → Flyway 校验迁移历史和 checksum → 执行尚未应用的迁移 → 应用上下文继续启动。
- 健康链路：`GET /actuator/health/readiness` → Actuator readiness group → MySQL 与 Redis 健康检查 → 返回 `UP` 或 `DOWN`。
- Qdrant 链路：`GET /actuator/health/qdrant` → `QdrantHealthIndicator.health()` → HTTP `GET /readyz` → 正常时 `UP`，不可达时 `DEGRADED`。

## 我理解的核心设计

- 关键不变量：MySQL 是业务事实来源；Redis 是可丢失的会话/短期状态；Qdrant 是可以重建的派生索引。
- 关键不变量：已发布 Flyway 迁移只允许向前新增，不能回改历史文件；`validate-on-migrate` 和 checksum 发现历史漂移，`clean-disabled` 防止误清库。
- 关键不变量：自编写的建表迁移必须给每张表和每个字段写入中文 MySQL `COMMENT`，而不只是 SQL 行注释。
- readiness 只包含 `readinessState,db,redis`，因此 MySQL 或 Redis 不可用时实例不接收新流量。Qdrant 不在核心 readiness 中，故障时降级但不影响 MySQL 事实的正确性。
- 使用 Testcontainers 的真实 MySQL 8，而不是 H2，是为了验证 MySQL 方言、类型、排序规则、CHECK、元数据注释和 Flyway 行为。
- Redis 显式关闭持久化，表达其数据可以丢失；MySQL 和 Qdrant 使用命名卷，使容器重启不会自动删除持久数据。
- 事务/一致性边界：Flyway 负责数据库结构版本，不负责跨 MySQL、Redis、Qdrant 的分布式事务。未来业务应先提交 MySQL 事实，再通过可重试机制更新缓存或索引。
- 权限与安全边界：基础 Compose 默认不映射三项基础设施的宿主机端口；宿主机开发 override 也只绑定 `127.0.0.1`。
- 为什么不提前创建全部领域表：P0 只验证迁移机制和基础设施角色，提前建表会固化尚未经过后续领域设计验证的数据模型。

## 失败路径

- 触发方式：Redis 停止、Qdrant 不可达、数据库凭据错误、迁移 checksum 被修改，或者 SQL 不兼容 MySQL 8。
- 系统如何失败：Redis 不可用使 readiness 返回 HTTP 503 和 `DOWN`；Qdrant 不可用返回 `DEGRADED`；数据库或迁移失败会阻止 Server 完成启动。
- 数据是否保持正确：迁移失败时应用不能在未知 Schema 上提供服务；Qdrant 故障不改变 MySQL 事实；Redis 丢失不能导致业务事实丢失。
- 如何定位与恢复：查看 Actuator component、Server 启动日志和 Flyway schema history。历史迁移需要修正时新增前向迁移，不能覆盖旧脚本消除 checksum 错误。

## 测试证据

- `InfrastructureConnectivityIntegrationTest.connectsToRealMySql8AndRedis`：断言数据库是 MySQL 8、Redis 返回 `PONG`，并确认 Qdrant 容器运行。
- `FlywayMigrationIntegrationTest.migratesEmptyMySqlWithExpectedBaselineAndSingletonSettings`：验证迁移版本、单例数据和排序规则。
- `migrationDocumentsTableAndEveryColumnInMySqlMetadata`：查询 `information_schema`，验证表和所有字段的真实元数据注释。
- `repeatedMigrationDoesNotChangeSchema`：验证重复迁移不会再次执行。
- `changedPublishedMigrationFailsChecksumValidation`：验证回改已发布迁移会被 checksum 阻断。
- `RedisReadinessFailureIntegrationTest.unavailableRedisMakesReadinessFailClosed`：验证 Redis 故障时 readiness fail closed。
- `InfrastructureHealthIntegrationTest.unavailableQdrantReportsDegradedWithoutPretendingItIsFactStorage`：验证 Qdrant 的降级语义。
- 它不能证明什么：尚不能证明未来缓存重建、索引重建和业务补偿流程正确，也不能替代生产备份恢复演练。

## 仍不清楚的问题

- Redis 数据丢失后，哪些未来状态可以静默重建，哪些必须让用户重新登录或重新发起操作？
- Qdrant 重建期间，搜索接口应该降级、报错，还是回退到 MySQL 查询？

