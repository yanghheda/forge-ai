package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WorkItemStore {

    WorkItem create(
            long organizationId,
            long reporterUserId,
            WorkItemType type,
            String title,
            String description,
            WorkItemStatus status,
            WorkItemPriority priority,
            Long assigneeUserId,
            Instant dueAt);

    WorkItem createChild(
            long organizationId,
            long reporterUserId,
            WorkItemType type,
            long parentId,
            String title,
            String description,
            WorkItemStatus status,
            WorkItemPriority priority,
            Long assigneeUserId,
            Instant dueAt);

    Optional<WorkItem> findByIdAndScope(long organizationId, long workItemId);

    /* 按公司范围批量读取工作项，用于看板等聚合投影避免逐条查询。 */
    List<WorkItem> findByIdsAndScope(long organizationId, List<Long> workItemIds);

    WorkItemPage findPage(
            long organizationId,
            WorkItemType type,
            WorkItemStatus status,
            int page,
            int pageSize);

    boolean update(
            long organizationId,
            long workItemId,
            String title,
            String description,
            WorkItemPriority priority,
            Long assigneeUserId,
            Instant dueAt,
            long expectedVersion);
}
