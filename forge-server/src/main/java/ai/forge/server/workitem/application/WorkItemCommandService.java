package ai.forge.server.workitem.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.project.application.ProjectStore;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemType;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class WorkItemCommandService {

    /* 在真实资源范围内执行类型对应的最终服务端授权。 */
    private final PermissionEvaluator permissionEvaluator;

    /* 检查项目存在性与负责人有效项目成员范围。 */
    private final ProjectStore projectStore;

    /* 在一个本地事务中分配编号并持久化工作项聚合。 */
    private final WorkItemStore workItemStore;

    public WorkItemCommandService(
            PermissionEvaluator permissionEvaluator, ProjectStore projectStore, WorkItemStore workItemStore) {
        this.permissionEvaluator = permissionEvaluator;
        this.projectStore = projectStore;
        this.workItemStore = workItemStore;
    }

    public WorkItem create(
            long userId,
            long workspaceId,
            long projectId,
            WorkItemType type,
            String title,
            String description,
            WorkItemPriority priority,
            Long assigneeUserId,
            Instant dueAt) {
        permissionEvaluator.requireProject(userId, workspaceId, projectId, type.permissionResource() + ".create");
        requireActiveAssignee(workspaceId, projectId, assigneeUserId);
        return workItemStore.create(
                workspaceId,
                projectId,
                userId,
                type,
                normalizeTitle(title),
                normalizeDescription(description),
                type.initialStatus(),
                priority,
                assigneeUserId,
                dueAt);
    }

    public WorkItem update(
            long userId,
            long workspaceId,
            long projectId,
            long workItemId,
            String title,
            String description,
            WorkItemPriority priority,
            Long assigneeUserId,
            Instant dueAt,
            long expectedVersion) {
        WorkItem current = workItemStore.findByIdAndScope(workspaceId, projectId, workItemId)
                .orElseThrow(ResourceNotFoundException::new);
        permissionEvaluator.requireProject(
                userId, workspaceId, projectId, current.type().permissionResource() + ".edit");
        Long resolvedAssignee = assigneeUserId == null ? current.assigneeUserId() : assigneeUserId;
        requireActiveAssignee(workspaceId, projectId, resolvedAssignee);
        boolean updated = workItemStore.update(
                workspaceId,
                projectId,
                workItemId,
                title == null ? current.title() : normalizeTitle(title),
                description == null ? current.description() : normalizeDescription(description),
                priority == null ? current.priority() : priority,
                resolvedAssignee,
                dueAt == null ? current.dueAt() : dueAt,
                expectedVersion);
        if (!updated) {
            throw new VersionConflictException();
        }
        return workItemStore.findByIdAndScope(workspaceId, projectId, workItemId).orElseThrow();
    }

    private void requireActiveAssignee(long workspaceId, long projectId, Long assigneeUserId) {
        if (assigneeUserId != null && !projectStore.hasActiveMember(workspaceId, projectId, assigneeUserId)) {
            throw new ResourceNotFoundException();
        }
    }

    private String normalizeTitle(String title) {
        String normalized = title.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Work item title must not be blank");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        return description == null ? "" : description.trim();
    }
}
