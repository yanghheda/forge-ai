package ai.forge.server.document.infrastructure;

import ai.forge.server.document.application.DocumentIndexingService;
import ai.forge.server.document.application.DocumentIndexingStore;
import ai.forge.server.document.application.EmbeddingClient;
import ai.forge.server.document.application.IndexRetryPolicy;
import ai.forge.server.document.application.IndexedChunkPayload;
import ai.forge.server.document.application.VectorIndexClient;
import ai.forge.server.document.application.VectorIndexUnavailableException;
import ai.forge.server.document.domain.DocumentChunk;
import ai.forge.server.document.domain.DocumentChunker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test-unit")
public class DocumentIndexingWorker {

    /* 日志只暴露稳定错误码，不向输出写入文档正文。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentIndexingWorker.class);

    /* Outbox 领取与任务状态机的事务边界服务。 */
    private final DocumentIndexingService indexingService;

    /* 文本向量化端口。 */
    private final EmbeddingClient embeddingClient;

    /* 向量索引端口；所有调用都发生在数据库事务之外。 */
    private final VectorIndexClient vectorIndexClient;

    /* 失败重试与退避策略。 */
    private final IndexRetryPolicy retryPolicy;

    /* INDEXING 租约时长；超过后视为持有者失联并允许重新领取。 */
    private final Duration lease;

    public DocumentIndexingWorker(
            DocumentIndexingService indexingService,
            EmbeddingClient embeddingClient,
            VectorIndexClient vectorIndexClient,
            @Value("${forge.rag.indexer.max-attempts:5}") int maxAttempts,
            @Value("${forge.rag.indexer.backoff-base:10s}") Duration backoffBase,
            @Value("${forge.rag.indexer.lease:5m}") Duration lease) {
        this.indexingService = indexingService;
        this.embeddingClient = embeddingClient;
        this.vectorIndexClient = vectorIndexClient;
        this.retryPolicy = new IndexRetryPolicy(maxAttempts, backoffBase);
        this.lease = lease;
    }

    /* 单次调度：先把 Outbox 事件落成任务，再逐个处理到无可处理任务。 */
    public void runOnce() {
        dispatchOutbox();
        while (processNextJob()) {
            /* 逐任务处理，避免长时间占用单一线程。 */
        }
    }

    /* 领取发布事件并幂等创建索引任务；返回本批事件数量。 */
    public int dispatchOutbox() {
        return indexingService.dispatchPublishedEvents();
    }

    /* 处理一个到期任务；返回是否处理了任务。 */
    public boolean processNextJob() {
        DocumentIndexingStore.DocumentIndexJob job = indexingService.claimNextIndexJob(lease)
                .orElse(null);
        if (job == null) {
            return false;
        }
        try {
            index(job);
            return true;
        } catch (Exception exception) {
            fail(job, exception);
            return true;
        }
    }

    /* 外部调用链：切块、向量化、覆盖写入、清除旧版本并校验数量；全程不持有数据库锁。 */
    private void index(DocumentIndexingStore.DocumentIndexJob job) {
        DocumentIndexingStore.IndexingFact fact = indexingService.loadIndexingFact(job)
                .orElseThrow(() -> new IllegalStateException("index job references missing rows"));
        String collectionName = collectionName();
        if (fact.documentRetired()) {
            vectorIndexClient.ensureCollection(collectionName, embeddingClient.dimension());
            vectorIndexClient.deleteStaleVersions(collectionName, job.documentId(), -1);
            indexingService.markIndexJobSucceeded(job.id(), 0);
            return;
        }
        List<DocumentChunk> chunks = DocumentChunker.chunk(fact.plainText());
        List<VectorIndexClient.ChunkPoint> points = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            IndexedChunkPayload payload = new IndexedChunkPayload(
                    fact.organizationId(), fact.workItemId(), job.documentId(),
                    job.versionId(), fact.documentType(), fact.visibility(), fact.contentHash(),
                    chunk.chunkIndex(), fact.title(), chunk.text());
            points.add(new VectorIndexClient.ChunkPoint(embeddingClient.embed(chunk.text()), payload));
        }
        vectorIndexClient.ensureCollection(collectionName, embeddingClient.dimension());
        vectorIndexClient.upsertChunks(collectionName, points);
        vectorIndexClient.deleteStaleVersions(collectionName, job.documentId(), job.versionId());
        long indexedCount = vectorIndexClient.countVersionChunks(
                collectionName, job.documentId(), job.versionId());
        if (indexedCount != points.size()) {
            throw new IllegalStateException(
                    "index verification failed: expected " + points.size() + " but got " + indexedCount);
        }
        indexingService.markIndexJobSucceeded(job.id(), points.size());
    }

    private void fail(DocumentIndexingStore.DocumentIndexJob job, Exception exception) {
        String errorCode = exception instanceof VectorIndexUnavailableException unavailable
                ? unavailable.errorCode()
                : "INDEX_FAILED";
        indexingService.markIndexJobFailed(job.id(), errorCode, exception.getMessage(),
                retryPolicy.maxAttempts(), retryPolicy.backoffAfter(job.attempts() + 1));
        LOGGER.error("document index job failed: jobId={}, documentId={}, versionId={}, errorCode={}, reason={}",
                job.id(), job.documentId(), job.versionId(), errorCode, exception.getMessage());
    }

    /* 集合名由 embedding 模型版本派生；切换模型时重建集合并原子切换别名。 */
    private String collectionName() {
        return "documents_" + embeddingClient.modelVersion();
    }
}
