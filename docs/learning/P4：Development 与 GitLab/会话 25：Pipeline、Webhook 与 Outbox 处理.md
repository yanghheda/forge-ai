# 会话 25：Pipeline、Webhook 与 Outbox 处理

## 我完成了什么

- 新增 `pipeline_runs` 与 `webhook_deliveries`，所有表和字段都带中文 MySQL 元数据注释。
- 新增受 `repo.write` 保护的 Pipeline 触发、最近快照查询与有界 Job 日志尾部 API。
- 新增 Webhook Secret 加密配置入口；Secret 继续由 `SecretService` 管理，不进入响应和日志。
- 新增 GitLab Webhook 快速接收入口：大小限制、常量时间 Secret 比较、delivery key、唯一键去重后立即返回 `202 Accepted`。
- 新增异步 Processor：使用租约领取、指数退避、未知事件安全忽略，将 MR/Pipeline payload 标准化为领域变化。
- MR/Pipeline 快照与 Outbox 在同一事务提交；通过远端 `updated_at` 严格递增规则抵御重复与乱序事件。
- Webhook 原始 JSON 只在待处理期间短期保存，完成或忽略后立即置空。
- 项目 Development 区域新增 Pipeline 面板，支持触发并以 3 秒轮询展示最终一致状态。

## 我理解的核心设计

### 调用链

Pipeline 主动触发：

`PipelinePanel → PipelineController → PipelineService → GitLabHttpClient → GitLab → MybatisPipelineStore → pipeline_runs`

Webhook 被动同步：

`GitLab → WebhookController → WebhookService → webhook_deliveries → 202`

随后异步执行：

`WebhookScheduler → WebhookProcessor → WebhookParser → MybatisWebhookStore → MR/Pipeline 快照 + outbox_events`

### 数据与事务边界

- Pipeline 触发前的本地上下文读取、GitLab HTTP 请求和返回快照保存是三个边界；外部请求不持有数据库锁。
- Webhook 接收事务只完成 delivery 去重入库，不执行快照更新或其他外部调用。
- Processor 每次领取一个 delivery；领取事务只设置 `PROCESSING` 与租约，解析发生在事务外。
- 最终处理事务锁定目标快照的远端时间，只有 `candidate.updatedAt > current.updatedAt` 才 upsert，并在同一事务写 Outbox、完成 delivery、清空 payload。
- MySQL 是 delivery、Pipeline/MR 快照和 Outbox 的事实来源；页面明确展示 `lastSyncedAt`，不假装与 GitLab 强一致。

### 幂等与乱序

- delivery key 优先使用 `X-Gitlab-Event-UUID`。
- 缺少 UUID 时使用 `connectionId + eventType + objectId + updatedAt + payloadHash` 的 SHA-256。
- `(connection_id, delivery_key)` 唯一键处理至少一次投递。
- 不依赖 JDBC affected-row 判断是否发生业务变化；Processor 在事务内 `FOR UPDATE` 读取当前远端时间，严格较新才写快照和 Outbox。

## 失败路径

- Secret 缺失或不匹配返回 `401 WEBHOOK_REJECTED`。
- payload 超过配置上限返回 `413 WEBHOOK_REJECTED`，且不进入数据库。
- 已存在 delivery 返回 `202` 和 `duplicate=true`，不重复处理。
- 未识别事件进入 `IGNORED` 并清空 payload，不产生领域变化。
- 已知事件格式错误进入 `FAILED`，按指数退避重试；达到上限后进入 `DEAD`。
- 旧或同时间事件进入 `PROCESSED`，但不覆盖新快照、不产生重复 Outbox。
- GitLab Pipeline HTTP 错误继续使用既有稳定错误码；日志只读取和返回配置上限内的内容。

## 测试证据

- `PipelineServiceTest`：触发调用边界、本地保存、日志尾部截断。
- `WebhookServiceTest`：合法/非法 Secret、超大 payload、UUID 去重、fallback key 稳定性。
- `WebhookProcessorTest`：Pipeline 映射、未知事件忽略、解析失败退避。
- `WebhookPersistenceIntegrationTest`（MySQL 8.4 Testcontainers）：旧/同时间事件不覆盖、不重复 Outbox，payload 完成后清空。
- `FlywayMigrationIntegrationTest`（MySQL 8.4 Testcontainers）：19 个迁移成功执行，新表/列中文注释与 `repo.write` 授权种子通过。
- `GitLabHttpClientTest`：GitLab 认证/错误映射回归、Pipeline 触发和有界日志读取。
- Web `pipeline-api.test.ts`、TypeScript、ESLint：Pipeline API 参数与页面静态质量通过。

## 风险与遗留问题

- 当前 Development UI 使用 3 秒轮询，不是专用业务 SSE；复用通用事件流需要先设计非 Agent Run 的 sequence 与重放契约，不能在本轮顺手扩展。
- Webhook Secret 当前支持安全替换，但旧 Secret 密文不会自动物理清理；后续应有受审计的 Secret 生命周期清理任务。
- Processor 能恢复数据库内的失败 delivery；GitLab 长时间不可达或漏投仍需要会话 27 的定时 reconcile 补偿。
- 日志接口按字节读取，截断点可能位于多字节字符中并由 UTF-8 解码器替换；不影响大小边界，但后续可优化为字符边界回退。
- 本轮不推进 Development → QA Guard，也不根据 Pipeline 成功自动跨越 ForgeAI 工作流状态。

## 复盘问题

1. 为什么 Webhook 必须先可靠入库并返回 `202`，而不能在 Controller 内直接更新 MR/Pipeline？
2. 为什么只靠 delivery 唯一键仍不足以抵御不同 UUID 的重复内容与乱序事件？
3. Webhook 已经能同步状态后，为什么仍需要定时 reconcile，它应以哪个系统为最终事实来源？
