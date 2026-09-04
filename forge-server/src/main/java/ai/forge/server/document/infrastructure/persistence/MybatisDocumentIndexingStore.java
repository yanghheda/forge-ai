package ai.forge.server.document.infrastructure.persistence;

import ai.forge.server.document.application.DocumentIndexingStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test-unit")
public class MybatisDocumentIndexingStore implements DocumentIndexingStore {

    /* 执行显式范围 SQL 的 MyBatis Mapper。 */
    private final DocumentIndexingMapper mapper;

    /* 解析 Outbox JSON 载荷。 */
    private final ObjectMapper objectMapper;

    public MybatisDocumentIndexingStore(DocumentIndexingMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PublishedDocumentEvent> claimPublishedEvents(int limit) {
        return mapper.claimOutboxEvents(limit).stream().map(this::publishedEvent).toList();
    }

    @Override
    public void createIndexJob(PublishedDocumentEvent event) {
        mapper.insertIndexJob(event.workspaceId(), event.projectId(), event.documentId(),
                event.versionId());
    }

    @Override
    public void markOutboxProcessed(List<Long> outboxIds) {
        mapper.markOutboxProcessed(outboxIds);
    }

    @Override
    public Optional<DocumentIndexJob> claimNextIndexJob(Duration lease) {
        long leaseSeconds = Math.max(lease.toMillis() / 1000, 1);
        Map<String, Object> row = mapper.claimIndexJobRow(leaseSeconds);
        if (row == null) {
            return Optional.empty();
        }
        long jobId = number(row, "id");
        if (mapper.markIndexing(jobId, leaseSeconds) != 1) {
            return Optional.empty();
        }
        return Optional.of(new DocumentIndexJob(jobId, number(row, "workspace_id"),
                number(row, "project_id"), number(row, "document_id"), number(row, "version_id"),
                (int) number(row, "attempts")));
    }

    @Override
    public Optional<IndexingFact> loadIndexingFact(DocumentIndexJob job) {
        Map<String, Object> row = mapper.findIndexingFactRow(job.id());
        if (row == null) {
            return Optional.empty();
        }
        boolean retired = row.get("deleted_at") != null
                || "ARCHIVED".equals(row.get("document_status").toString());
        return Optional.of(new IndexingFact(retired, number(row, "workspace_id"),
                number(row, "project_id"), nullable(row, "work_item_id"),
                row.get("document_type").toString(), row.get("title").toString(),
                row.get("visibility").toString(), row.get("plain_text").toString(),
                row.get("content_hash").toString()));
    }

    @Override
    public void markIndexJobSucceeded(long jobId, int chunkCount) {
        mapper.markSucceeded(jobId, chunkCount);
    }

    @Override
    public void markIndexJobFailed(
            long jobId, String errorCode, String errorMessage, int maxAttempts, Duration backoff) {
        long backoffMicros = backoff.toMillis() * 1000;
        mapper.markFailed(jobId, maxAttempts, backoffMicros, errorCode,
                truncate(errorMessage));
    }

    private PublishedDocumentEvent publishedEvent(Map<String, Object> row) {
        try {
            JsonNode payload = objectMapper.readTree(row.get("payload").toString());
            return new PublishedDocumentEvent(number(row, "id"), payload.path("workspaceId").asLong(),
                    payload.path("projectId").asLong(), payload.path("documentId").asLong(),
                    payload.path("versionId").asLong());
        } catch (Exception exception) {
            throw new IllegalStateException("published outbox payload is invalid", exception);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private Long nullable(Map<String, Object> row, String key) {
        return row.get(key) == null ? null : number(row, key);
    }
}
