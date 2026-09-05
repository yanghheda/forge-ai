package ai.forge.server.gitlab.infrastructure.persistence;

import ai.forge.server.gitlab.application.EncryptedSecret;
import ai.forge.server.gitlab.application.SecretService;
import ai.forge.server.gitlab.application.WebhookChange;
import ai.forge.server.gitlab.application.WebhookDelivery;
import ai.forge.server.gitlab.application.WebhookStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test-unit")
public class MybatisWebhookStore implements WebhookStore {

    /* 所有领取与范围 upsert 都通过显式 MyBatis SQL 完成。 */
    private final WebhookMapper mapper;

    /* Webhook Secret 只在常量时间比较前短暂解密。 */
    private final SecretService secrets;

    /* 只序列化无原始正文的标准化 Outbox payload。 */
    private final ObjectMapper objectMapper;

    public MybatisWebhookStore(WebhookMapper mapper, SecretService secrets, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.secrets = secrets;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<String> findSecret(long connectionId) {
        return mapper.findWebhookSecret(connectionId).stream().findFirst().map(row -> secrets.decrypt(
                number(row, "workspace_id"), text(row, "type"), new EncryptedSecret(
                        text(row, "ciphertext"), text(row, "iv"), ((Number) row.get("key_version")).intValue(),
                        text(row, "fingerprint"))));
    }

    @Override
    @Transactional
    public boolean accept(long connectionId, String deliveryKey, String eventType, String payloadHash, String payload) {
        return mapper.insertDelivery(connectionId, deliveryKey, eventType, payloadHash, payload) == 1;
    }

    @Override
    @Transactional
    public Optional<WebhookDelivery> claimNext(Duration lease) {
        Map<String, Object> row = mapper.claim().stream().findFirst().orElse(null);
        if (row == null) {
            return Optional.empty();
        }
        mapper.markProcessing(number(row, "id"), lease.toSeconds());
        return Optional.of(new WebhookDelivery(number(row, "id"), number(row, "workspace_id"),
                number(row, "connection_id"), text(row, "delivery_key"), text(row, "event_type"),
                text(row, "payload_hash"), text(row, "payload"), ((Number) row.get("attempts")).intValue(),
                (LocalDateTime) row.get("received_at")));
    }

    @Override
    @Transactional
    public void process(WebhookDelivery delivery, WebhookChange change) {
        Map<String, Object> repository = mapper.findRepository(delivery.connectionId(), remoteProjectId(change))
                .stream().findFirst().orElse(null);
        if (repository == null) {
            mapper.markDone(delivery.id(), "IGNORED");
            return;
        }
        long workspaceId = number(repository, "workspace_id");
        long repositoryId = number(repository, "id");
        int affected;
        String aggregateType;
        long aggregateId;
        String eventType;
        if (change instanceof WebhookChange.MergeRequestChanged mr) {
            if (!isNewer(mapper.lockMergeRequestTime(workspaceId, repositoryId, mr.remoteMrIid()),
                    mr.remoteUpdatedAt())) {
                mapper.markDone(delivery.id(), "PROCESSED");
                return;
            }
            affected = mapper.upsertMergeRequest(workspaceId, repositoryId, mr.remoteMrIid(), mr.title(),
                    mr.sourceBranch(), mr.targetBranch(), mr.state(), mr.webUrl(), mr.authorExternalId(),
                    mr.headSha(), mr.mergeStatus(), mr.remoteUpdatedAt());
            aggregateType = "MERGE_REQUEST";
            aggregateId = mr.remoteMrIid();
            eventType = "MERGE_REQUEST_CHANGED";
        } else {
            WebhookChange.PipelineChanged pipeline = (WebhookChange.PipelineChanged) change;
            if (!isNewer(mapper.lockPipelineTime(workspaceId, repositoryId, pipeline.remotePipelineId()),
                    pipeline.remoteUpdatedAt())) {
                mapper.markDone(delivery.id(), "PROCESSED");
                return;
            }
            affected = mapper.upsertPipeline(workspaceId, repositoryId, pipeline.remotePipelineId(), pipeline.ref(),
                    pipeline.commitSha(), pipeline.status(), pipeline.webUrl(), pipeline.startedAt(),
                    pipeline.finishedAt(), pipeline.remoteUpdatedAt());
            aggregateType = "PIPELINE";
            aggregateId = pipeline.remotePipelineId();
            eventType = "PIPELINE_CHANGED";
        }
        if (affected > 0) {
            mapper.insertOutbox(aggregateType, aggregateId, eventType, outboxPayload(
                    workspaceId, repositoryId, aggregateId, delivery.deliveryKey()));
        }
        mapper.markDone(delivery.id(), "PROCESSED");
    }

    @Override
    @Transactional
    public void ignore(WebhookDelivery delivery) {
        mapper.markDone(delivery.id(), "IGNORED");
    }

    @Override
    @Transactional
    public void fail(WebhookDelivery delivery, String errorMessage, int maxAttempts, Duration backoff) {
        mapper.markFailed(delivery.id(), maxAttempts, backoff.toNanos() / 1_000L, errorMessage);
    }

    private String outboxPayload(long workspaceId, long repositoryId, long remoteId, String deliveryKey) {
        try {
            return objectMapper.writeValueAsString(Map.of("workspaceId", workspaceId, "repositoryId", repositoryId,
                    "remoteId", remoteId, "deliveryKey", deliveryKey));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String remoteProjectId(WebhookChange change) {
        return change instanceof WebhookChange.MergeRequestChanged mr
                ? mr.remoteProjectId() : ((WebhookChange.PipelineChanged) change).remoteProjectId();
    }

    private static boolean isNewer(List<LocalDateTime> currentTimes, LocalDateTime candidate) {
        return currentTimes.isEmpty() || candidate.isAfter(currentTimes.getFirst());
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : value.toString();
    }
}
