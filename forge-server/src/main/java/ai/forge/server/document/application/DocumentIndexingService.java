package ai.forge.server.document.application;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test-unit")
public class DocumentIndexingService {

    /* 单次调度最多领取的 Outbox 事件数量；限制单事务持锁时长。 */
    private final int dispatchBatchSize;

    /* Outbox 事件到派生索引任务的持久化端口。 */
    private final DocumentIndexingStore store;

    public DocumentIndexingService(
            @Value("${forge.rag.indexer.dispatch-batch-size:50}") int dispatchBatchSize,
            DocumentIndexingStore store) {
        this.dispatchBatchSize = dispatchBatchSize;
        this.store = store;
    }

    /* 领取未消费发布事件并幂等落库为索引任务；任务创建与 Outbox 回写同事务。 */
    @Transactional
    public int dispatchPublishedEvents() {
        List<DocumentIndexingStore.PublishedDocumentEvent> events =
                store.claimPublishedEvents(dispatchBatchSize);
        if (events.isEmpty()) {
            return 0;
        }
        for (DocumentIndexingStore.PublishedDocumentEvent event : events) {
            store.createIndexJob(event);
        }
        store.markOutboxProcessed(events.stream()
                .map(DocumentIndexingStore.PublishedDocumentEvent::outboxId)
                .toList());
        return events.size();
    }

    /* 领取一个到期任务并置为 INDEXING；租约过期允许其他实例重新领取。 */
    @Transactional
    public Optional<DocumentIndexingStore.DocumentIndexJob> claimNextIndexJob(Duration lease) {
        return store.claimNextIndexJob(lease);
    }

    /* 读取任务对应的文档与版本事实；只读操作不开启写事务。 */
    public Optional<DocumentIndexingStore.IndexingFact> loadIndexingFact(
            DocumentIndexingStore.DocumentIndexJob job) {
        return store.loadIndexingFact(job);
    }

    /* 索引成功后独立提交的状态回写；外部调用必须已完成。 */
    @Transactional
    public void markIndexJobSucceeded(long jobId, int chunkCount) {
        store.markIndexJobSucceeded(jobId, chunkCount);
    }

    /* 索引失败后的退避重试状态回写；达到上限由 Store 转 DEAD。 */
    @Transactional
    public void markIndexJobFailed(
            long jobId, String errorCode, String errorMessage, int maxAttempts, Duration backoff) {
        store.markIndexJobFailed(jobId, errorCode, errorMessage, maxAttempts, backoff);
    }
}
