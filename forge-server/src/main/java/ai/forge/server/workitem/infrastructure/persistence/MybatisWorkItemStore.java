package ai.forge.server.workitem.infrastructure.persistence;

import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.workitem.application.WorkItemPage;
import ai.forge.server.workitem.application.WorkItemSummary;
import ai.forge.server.workitem.application.WorkItemStore;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test-unit")
public class MybatisWorkItemStore implements WorkItemStore {

    /* 执行显式带 Workspace/Project scope、行锁与乐观锁的 MyBatis SQL。 */
    private final WorkItemMapper mapper;

    public MybatisWorkItemStore(WorkItemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public WorkItem create(
            long workspaceId,
            long projectId,
            long reporterUserId,
            WorkItemType type,
            String title,
            String description,
            WorkItemStatus status,
            WorkItemPriority priority,
            Long assigneeUserId,
            Instant dueAt) {
        return insertWorkItem(
                workspaceId,
                projectId,
                reporterUserId,
                type,
                null,
                title,
                description,
                status,
                priority,
                assigneeUserId,
                dueAt);
    }

    @Override
    @Transactional
    public WorkItem createChild(
            long workspaceId,
            long projectId,
            long reporterUserId,
            WorkItemType type,
            long parentId,
            String title,
            String description,
            WorkItemStatus status,
            WorkItemPriority priority,
            Long assigneeUserId,
            Instant dueAt) {
        return insertWorkItem(
                workspaceId,
                projectId,
                reporterUserId,
                type,
                parentId,
                title,
                description,
                status,
                priority,
                assigneeUserId,
                dueAt);
    }

    /* 在同一事务内锁定项目编号序列并写入工作项；parentId 为空表示顶层工作项。 */
    private WorkItem insertWorkItem(
            long workspaceId,
            long projectId,
            long reporterUserId,
            WorkItemType type,
            Long parentId,
            String title,
            String description,
            WorkItemStatus status,
            WorkItemPriority priority,
            Long assigneeUserId,
            Instant dueAt) {
        Map<String, Object> sequence = mapper.lockSequence(workspaceId, projectId).stream()
                .findFirst()
                .orElseThrow(ResourceNotFoundException::new);
        long itemNumber = number(sequence, "next_value");
        if (mapper.consumeSequence(projectId, itemNumber) != 1) {
            throw new IllegalStateException("Locked work item sequence changed unexpectedly");
        }
        String itemKey = text(sequence, "project_key") + "-" + itemNumber;
        mapper.insert(
                workspaceId,
                projectId,
                itemNumber,
                itemKey,
                type.name(),
                title,
                description,
                status.name(),
                priority.name(),
                parentId,
                assigneeUserId,
                reporterUserId,
                dueAt);
        long workItemId = mapper.lastInsertId();
        return findByIdAndScope(workspaceId, projectId, workItemId).orElseThrow();
    }

    @Override
    public Optional<WorkItem> findByIdAndScope(long workspaceId, long projectId, long workItemId) {
        return mapper.findByIdAndScope(workspaceId, projectId, workItemId).stream()
                .findFirst()
                .map(this::workItem);
    }

    @Override
    public WorkItemPage findPage(
            long workspaceId,
            long projectId,
            WorkItemType type,
            WorkItemStatus status,
            int page,
            int pageSize) {
        String typeValue = type == null ? null : type.name();
        String statusValue = status == null ? null : status.name();
        List<WorkItemSummary> items = mapper.findPage(
                        workspaceId, projectId, typeValue, statusValue, pageSize, (page - 1) * pageSize)
                .stream()
                .map(this::workItemSummary)
                .toList();
        return new WorkItemPage(
                items, page, pageSize, mapper.countPage(workspaceId, projectId, typeValue, statusValue));
    }

    private WorkItemSummary workItemSummary(Map<String, Object> row) {
        return new WorkItemSummary(
                number(row, "id"),
                number(row, "workspace_id"),
                number(row, "project_id"),
                number(row, "item_number"),
                text(row, "item_key"),
                WorkItemType.valueOf(text(row, "type")),
                text(row, "title"),
                WorkItemStatus.valueOf(text(row, "status")),
                WorkItemPriority.valueOf(text(row, "priority")),
                nullableNumber(row, "assignee_user_id"),
                instant(row.get("due_at")),
                number(row, "version"),
                instant(row.get("created_at")),
                instant(row.get("updated_at")));
    }

    @Override
    public boolean update(
            long workspaceId,
            long projectId,
            long workItemId,
            String title,
            String description,
            WorkItemPriority priority,
            Long assigneeUserId,
            Instant dueAt,
            long expectedVersion) {
        return mapper.update(
                        workspaceId,
                        projectId,
                        workItemId,
                        title,
                        description,
                        priority.name(),
                        assigneeUserId,
                        dueAt,
                        expectedVersion)
                == 1;
    }

    private WorkItem workItem(Map<String, Object> row) {
        return new WorkItem(
                number(row, "id"),
                number(row, "workspace_id"),
                number(row, "project_id"),
                number(row, "item_number"),
                text(row, "item_key"),
                WorkItemType.valueOf(text(row, "type")),
                text(row, "title"),
                text(row, "description"),
                WorkItemStatus.valueOf(text(row, "status")),
                WorkItemPriority.valueOf(text(row, "priority")),
                nullableNumber(row, "assignee_user_id"),
                number(row, "reporter_user_id"),
                instant(row.get("due_at")),
                number(row, "version"),
                instant(row.get("created_at")),
                instant(row.get("updated_at")));
    }

    private Instant instant(Object value) {
        return value == null ? null : ((LocalDateTime) value).toInstant(ZoneOffset.UTC);
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private Long nullableNumber(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : ((Number) value).longValue();
    }

    private String text(Map<String, Object> row, String key) {
        return row.get(key).toString();
    }
}
