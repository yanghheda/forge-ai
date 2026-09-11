package ai.forge.server.workitem.infrastructure.persistence;

import ai.forge.server.workitem.application.WorkItemCollaborationStore;
import ai.forge.server.workitem.domain.ActivityItem;
import ai.forge.server.workitem.domain.RelationConflictException;
import ai.forge.server.workitem.domain.WorkItemLabel;
import ai.forge.server.workitem.domain.WorkItemRelation;
import ai.forge.server.workitem.domain.WorkItemRelationType;
import ai.forge.server.workitem.domain.WorkflowAction;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test-unit")
public class MybatisWorkItemCollaborationStore implements WorkItemCollaborationStore {

    /* 执行关系、标签、评论和 Activity 的带 scope SQL。 */
    private final WorkItemCollaborationMapper mapper;

    public MybatisWorkItemCollaborationStore(WorkItemCollaborationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean relationExists(
            long organizationId,
            long sourceId,
            long targetId,
            WorkItemRelationType relationType) {
        return mapper.countRelation(organizationId, sourceId, targetId, relationType.name()) > 0;
    }

    @Override
    public WorkItemRelation createRelation(
            long organizationId,
            long sourceId,
            long targetId,
            WorkItemRelationType relationType,
            long createdBy) {
        try {
            mapper.insertRelation(organizationId, sourceId, targetId, relationType.name(), createdBy);
        } catch (DuplicateKeyException exception) {
            throw new RelationConflictException();
        }
        long id = mapper.lastInsertId();
        return mapper.findRelations(organizationId, sourceId).stream()
                .map(this::relation)
                .filter(value -> value.id() == id)
                .findFirst()
                .orElseThrow();
    }

    @Override
    public List<WorkItemRelation> findRelations(long organizationId, long workItemId) {
        return mapper.findRelations(organizationId, workItemId).stream().map(this::relation).toList();
    }

    @Override
    public void addLabel(
            long organizationId, long workItemId, WorkItemLabel label, long createdBy) {
        mapper.insertLabel(organizationId, workItemId, label.name(), createdBy);
    }

    @Override
    public ActivityItem createComment(
            long organizationId, long workItemId, long authorId, String body) {
        mapper.insertComment(organizationId, workItemId, authorId, body);
        long id = mapper.lastInsertId();
        return findActivity(organizationId, workItemId).stream()
                .filter(item -> item.kind().equals("COMMENT") && item.id() == id)
                .findFirst()
                .orElseThrow();
    }

    @Override
    public List<ActivityItem> findActivity(long organizationId, long workItemId) {
        return mapper.findActivity(organizationId, workItemId).stream().map(this::activityItem).toList();
    }

    private WorkItemRelation relation(Map<String, Object> row) {
        return new WorkItemRelation(
                number(row, "id"),
                number(row, "source_id"),
                number(row, "target_id"),
                WorkItemRelationType.valueOf(text(row, "relation_type")),
                number(row, "created_by"),
                instant(row, "created_at"));
    }

    private ActivityItem activityItem(Map<String, Object> row) {
        String eventType = nullableText(row, "event_type");
        return new ActivityItem(
                text(row, "kind"),
                number(row, "id"),
                number(row, "actor_id"),
                eventType == null ? null : WorkflowAction.valueOf(eventType),
                nullableText(row, "reason"),
                nullableText(row, "body"),
                instant(row, "created_at"));
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private String text(Map<String, Object> row, String key) {
        return row.get(key).toString();
    }

    private String nullableText(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private java.time.Instant instant(Map<String, Object> row, String key) {
        return ((LocalDateTime) row.get(key)).toInstant(ZoneOffset.UTC);
    }
}
