package ai.forge.server.workitem.infrastructure.persistence;

import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.workitem.application.RequirementMaterialStore;
import ai.forge.server.workitem.application.RequirementTransitionStore;
import ai.forge.server.workitem.domain.IdempotencyConflictException;
import ai.forge.server.workitem.domain.TransitionDefinition;
import ai.forge.server.workitem.domain.WorkItemEvent;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkflowAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test-unit")
public class MybatisRequirementWorkflowStore implements RequirementMaterialStore, RequirementTransitionStore {

    /* 执行带 Workspace/Project scope 的 Requirement 工作流 SQL。 */
    private final RequirementWorkflowMapper mapper;

    /* 解析事件元数据中的聚合版本，支持稳定幂等重放。 */
    private final ObjectMapper objectMapper;

    public MybatisRequirementWorkflowStore(RequirementWorkflowMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<RequirementMaterial> find(long workspaceId, long projectId, long workItemId) {
        return mapper.findMaterial(workspaceId, projectId, workItemId).stream()
                .findFirst()
                .map(row -> new RequirementMaterial(
                        text(row, "goal"), text(row, "in_scope"), number(row, "acceptance_count").intValue()));
    }

    @Override
    public boolean hasPublishedPrd(long workspaceId, long projectId, long workItemId) {
        return mapper.countPublishedPrd(workspaceId, projectId, workItemId) > 0;
    }

    @Override
    public Optional<TransitionResult> findResultByIdempotencyKey(
            long workspaceId, long projectId, long workItemId, String idempotencyKey) {
        return mapper.findIdempotentEvent(workspaceId, projectId, workItemId, idempotencyKey).stream()
                .findFirst()
                .map(this::transitionResult);
    }

    @Override
    public TransitionResult transition(
            long workspaceId,
            long projectId,
            long workItemId,
            long actorId,
            long expectedVersion,
            String idempotencyKey,
            String reason,
            TransitionDefinition definition) {
        if (mapper.updateStatus(
                        workspaceId,
                        projectId,
                        workItemId,
                        definition.from().name(),
                        definition.to().name(),
                        expectedVersion)
                != 1) {
            Optional<TransitionResult> concurrentReplay =
                    findResultByIdempotencyKey(workspaceId, projectId, workItemId, idempotencyKey);
            if (concurrentReplay.isPresent()) {
                if (concurrentReplay.get().action() != definition.action()) {
                    throw new IdempotencyConflictException();
                }
                return concurrentReplay.get();
            }
            throw new VersionConflictException();
        }
        boolean rejection = definition.action() == WorkflowAction.REJECT_PRODUCT_REVIEW;
        boolean approval = definition.action() == WorkflowAction.APPROVE_PRODUCT_REVIEW;
        mapper.insertReview(
                workspaceId,
                projectId,
                workItemId,
                rejection ? "REJECTED" : approval ? "APPROVED" : "SUBMITTED",
                rejection || approval ? actorId : null,
                rejection ? reason : null);
        long nextVersion = expectedVersion + 1;
        mapper.insertEvent(
                workspaceId,
                projectId,
                workItemId,
                definition.action().name(),
                definition.from().name(),
                definition.to().name(),
                actorId,
                reason,
                nextVersion,
                idempotencyKey);
        return new TransitionResult(definition.action(), definition.to(), nextVersion, mapper.lastInsertId());
    }

    @Override
    public List<WorkItemEvent> findEvents(long workspaceId, long projectId, long workItemId) {
        return mapper.findEvents(workspaceId, projectId, workItemId).stream().map(this::event).toList();
    }

    private TransitionResult transitionResult(Map<String, Object> row) {
        try {
            JsonNode metadata = objectMapper.readTree(text(row, "metadata_json"));
            return new TransitionResult(
                    WorkflowAction.valueOf(text(row, "event_type")),
                    WorkItemStatus.valueOf(text(row, "to_status")),
                    metadata.get("version").asLong(),
                    number(row, "id").longValue());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored workflow event metadata is invalid", exception);
        }
    }

    private WorkItemEvent event(Map<String, Object> row) {
        return new WorkItemEvent(
                number(row, "id").longValue(),
                number(row, "workspace_id").longValue(),
                number(row, "project_id").longValue(),
                number(row, "work_item_id").longValue(),
                WorkflowAction.valueOf(text(row, "event_type")),
                WorkItemStatus.valueOf(text(row, "from_status")),
                WorkItemStatus.valueOf(text(row, "to_status")),
                number(row, "actor_id").longValue(),
                nullableText(row, "reason"),
                text(row, "idempotency_key"),
                ((LocalDateTime) row.get("created_at")).toInstant(ZoneOffset.UTC));
    }

    private Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }

    private String text(Map<String, Object> row, String key) {
        return row.get(key).toString();
    }

    private String nullableText(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }
}
