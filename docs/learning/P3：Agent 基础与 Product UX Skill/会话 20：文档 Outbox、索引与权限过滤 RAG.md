# 会话 20：文档 Outbox、索引与权限过滤 RAG

## 我完成了什么

- 发布文档时在发布事务内写 `DOCUMENT_VERSION_PUBLISHED` Outbox 事件（会话 18 已有事件写入，本轮补全消费侧）。
- `document_index_jobs` 表（V14 迁移）以 `(document_id, version_id)` 唯一键承载幂等索引任务，状态机为 `PENDING → INDEXING → SUCCEEDED / FAILED → DEAD`。
- `DocumentIndexingWorker` 完成 Outbox 领取 → 任务创建 → 切块 → 向量化 → Qdrant 覆盖写入 → 清除旧版本切片 → 数量校验的完整流水线；`DocumentIndexingScheduler` 以固定延迟驱动并可通过配置关闭。
- `DocumentChunker` 按段落切块并做 400 字符重叠，目标 2400 字符、硬上限 3600 字符。
- `RagSearchService` 作为检索门面：调用方只能传 query、documentType、topK；`workspace_id` 与 `project_id IN allowed` 由服务端从 `PermissionEvaluator.projectIdsWithPermission` 派生，构造为 Qdrant 强制 metadata filter。
- Qdrant 故障时检索抛 `RagUnavailableException` 稳定降级；索引失败退避重试，达到上限转 DEAD，不阻塞文档事实读写。

## 我理解的核心设计

- 调用链：`DocumentService.publish`（事务内写 Outbox）→ `dispatchPublishedEvents`（`FOR UPDATE SKIP LOCKED` 领取事件，同事务创建任务并回写 `processed_at`）→ `claimNextIndexJob`（置 INDEXING 并带租约）→ Worker 在事务外调用 Embedding 与 Qdrant → 成功/失败各自用独立短事务回写状态。
- 事务边界：所有外部调用（Qdrant HTTP、Embedding 计算）都在数据库事务之外；MySQL 里只有任务状态短事务。这是"外部系统调用不得放在持有行锁的事务中"的直接落地。
- 幂等性：任务表唯一键使重复 Outbox 事件只建一次任务；切片稳定 ID `doc:{documentId}:ver:{versionId}:chunk:{chunkIndex}` 派生 UUID，使重复 upsert 覆盖写入而不是追加。
- 权威边界：MySQL 是事实来源；Qdrant 是可重建的派生索引——删掉 Collection 后重放任务即可恢复，反之不成立。
- 权限边界：metadata filter（检索前过滤）与 prompt filter（检索后过滤）的安全差别在于：prompt 过滤可以被注入文本绕过且向量已经在越权数据上计算过相似度；只有服务端构造的强制 metadata filter 让越权文档根本不进入候选集。

## 失败路径

- Outbox 事件在任务创建同事务标记 `processed_at`；Worker 处理失败不影响事件已消费，重试由任务表状态机承担。
- 索引失败：`markFailed` 自增 attempts、按 `base * 2^(n-1)` 退避设置 `next_attempt_at`；达到 maxAttempts 转 DEAD 等待人工处理，文档读取不受影响。
- INDEXING 租约：任务领取时把租约写入 `next_attempt_at`；持有者崩溃后租约到期，其他实例可重新领取。
- Qdrant 不可用：检索抛 `RagUnavailableException`（稳定错误），Agent 明确降级而不是静默返回空结果——空结果会被误读为"没有相关文档"。

## 测试证据

- `DocumentChunkerTest`（5 例）：段落切分、超长段落二次切分、目标长度封口、重叠上下文重建、空文本。
- `DocumentRagIntegrationTest`（7 例，真实 MySQL + Qdrant 容器）：
  - `publishedDocumentIsIndexedAndSearchableWithVersionReference`：发布 → 任务 SUCCEEDED → 检索命中并携带 versionId；documentType 过滤生效。
  - `duplicateConsumptionDoesNotDuplicateChunks`：手工插入重复 Outbox 事件 → 任务数与切片数不变。
  - `versionSwitchRemovesStaleVersionChunks`：新版本索引后旧版本切片清零，检索引用新 versionId。
  - `crossWorkspaceQueriesAreBlockedByForcedFilter`：他区文档切片确实写入同一 Collection，但本区检索只命中本区文档；Owner 不属于第二个工作区时显式指定他区也拿不到结果。
  - `failedIndexingRetriesWithBackoffAndEventuallyDiesWithoutBlockingDocuments`：退避期内不可重复领取；第 3 次失败转 DEAD；事件保持已消费；文档 API 仍 200。
  - `qdrantOutageDegradesSearchToStableError`：死端点客户端触发 `RagUnavailableException`。
  - `searchValidatesQueryAndTopKBounds`：空查询与越界 topK 拒绝。
- `make ci` 全量通过（130 个 server 测试 + 前端构建 + 契约检查）。
- 这些证据尚不能证明：多实例并发领取的吞吐与公平性（SKIP LOCKED 只验证了正确性语义）、真实 Embedding 模型的检索质量、混合检索与 rerank（下一阶段）。

## 踩过的坑

- Qdrant REST 中 upsert 必须用 `PUT /collections/{name}/points`；`POST /points` 是按 `ids`/filter 改 payload 的端点，用错返回 400 且错误信息只有 "missing field ids"。错误消息里带响应体之后才定位到。
- MySQL 单表 UPDATE 的 SET 子句从左到右求值：`SET attempts = attempts + 1, status = IF(attempts >= ...)` 中 IF 里的 `attempts` 已是自增后的新值；第一次误写成 `IF(attempts + 1 >= ...)` 导致重试提前一次转 DEAD。
- Qdrant filter 条件必须有 `key` 字段；漏掉后错误是 "Expected some form of condition"，容易误判为语法问题。
- JdbcTemplate 的 `queryForObject(sql, class)` 不带参数版本会原样发送 `?` 给 MySQL，报的是语法错误而不是参数缺失，极具迷惑性。

## 仍不清楚的问题

- 混合检索（BM25 + dense + RRF + rerank）与 token budget 压缩在详细设计 9.8 中定义，属于后续会话；当前只做 dense retrieval，接口已保留 documentType 阶段信息。
- Embedding 目前是特征哈希 Fake；切换真实模型时 Collection 名按 modelVersion 派生、新集合重建后原子切换别名，这个迁移流程本轮只预留了命名约定，没有实现重建编排。
- 删除文档的失效路径（`documentRetired` 分支清除全部切片）已实现并覆盖，但归档文档的检索可见性策略（是否仍可检索历史版本）还没有产品决策。

## 3 个复盘问题

1. 为什么 Outbox 比"提交后直接调用 Qdrant"可靠？如果进程在发布事务提交后、索引调用前崩溃，两种方案分别会发生什么？
2. Qdrant 为什么不能作为文档事实来源？从"删除 Collection 能否恢复"和"权限判断发生在哪一层"两个角度说明。
3. 为什么"先全量召回再在 Prompt 里过滤掉越权文档"不安全？向量相似度计算发生在过滤的哪一侧，这对信息泄露意味着什么？
