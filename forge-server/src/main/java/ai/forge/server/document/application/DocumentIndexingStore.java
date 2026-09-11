package ai.forge.server.document.application;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Outbox 事件到派生索引任务之间的持久化端口；SQL 必须显式携带公司作用域。 */
public interface DocumentIndexingStore {

    /* 在事务内领取一批未消费的文档发布事件；使用 FOR UPDATE SKIP LOCKED 支持多实例。 */
    List<PublishedDocumentEvent> claimPublishedEvents(int limit);

    /* 以 (document_id, version_id) 唯一键幂等创建索引任务；重复事件不得重复建任务。 */
    void createIndexJob(PublishedDocumentEvent event);

    /* 标记 Outbox 事件完成异步交接；必须在创建任务的同一事务内调用。 */
    void markOutboxProcessed(List<Long> outboxIds);

    /* 领取下一个到期任务并置为 INDEXING；租约过期允许重新领取，返回空表示暂无可处理任务。 */
    Optional<DocumentIndexJob> claimNextIndexJob(Duration lease);

    /* 读取任务对应的文档与版本事实；行缺失或逻辑删除都通过结果字段表达。 */
    Optional<IndexingFact> loadIndexingFact(DocumentIndexJob job);

    /* 将任务标记为索引成功并记录切片数量。 */
    void markIndexJobSucceeded(long jobId, int chunkCount);

    /* 将任务标记为失败并安排退避重试；达到上限转 DEAD 等待人工处理。 */
    void markIndexJobFailed(long jobId, String errorCode, String errorMessage, int maxAttempts,
            Duration backoff);

    /** 从 Outbox 领取的文档发布事件载荷。 */
    record PublishedDocumentEvent(
            /* Outbox 事件标识；用于同一事务内回写 processed_at。 */ long outboxId,
            /* 事件归属工作区。 */ long organizationId,
            /* 发布的文档标识。 */ long documentId,
            /* 发布的不可变版本标识。 */ long versionId) {}

    /** 已领取待处理的索引任务。 */
    record DocumentIndexJob(
            /* 任务标识。 */ long id,
            /* 任务归属工作区。 */ long organizationId,
            /* 待索引文档标识。 */ long documentId,
            /* 待索引版本标识。 */ long versionId,
            /* 已失败尝试次数；用于计算下一次退避。 */ int attempts) {}

    /** 索引任务对应的文档与版本事实快照。 */
    record IndexingFact(
            /* 文档是否已逻辑删除或归档；为真时本轮应清除派生切片而不是写入。 */ boolean documentRetired,
            /* 文档归属工作区。 */ long organizationId,
            /* 可选关联工作项。 */ Long workItemId,
            /* 文档类型。 */ String documentType,
            /* 文档标题。 */ String title,
            /* 文档可见性。 */ String visibility,
            /* 版本规范化纯文本；切块唯一输入。 */ String plainText,
            /* 版本内容摘要。 */ String contentHash) {}
}
