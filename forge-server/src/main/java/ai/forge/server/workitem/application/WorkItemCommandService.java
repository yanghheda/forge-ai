package ai.forge.server.workitem.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.organization.application.OrganizationStore;
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

    /* 检查负责人是否为有效公司成员。 */
    private final OrganizationStore organizationStore;

    /* 在一个本地事务中分配编号并持久化工作项聚合。 */
    private final WorkItemStore workItemStore;

    public WorkItemCommandService(
            PermissionEvaluator permissionEvaluator, OrganizationStore organizationStore, WorkItemStore workItemStore) {
        this.permissionEvaluator = permissionEvaluator;
        this.organizationStore = organizationStore;
        this.workItemStore = workItemStore;
    }

    public WorkItem create(
            long userId,
            long organizationId,
            WorkItemType type,
            String title,
            String description,
            WorkItemPriority priority,
            Long assigneeUserId,
            Instant dueAt) {
        permissionEvaluator.requireOrganization(userId, organizationId, type.permissionResource() + ".create");
        requireActiveAssignee(organizationId, assigneeUserId);
        return workItemStore.create(
                organizationId,
                userId,
                type,
                normalizeTitle(title),
                normalizeDescription(description),
                type.initialStatus(),
                /* Tool 契约允许省略优先级；缺省值由服务端控制。 */
                priority == null ? WorkItemPriority.MEDIUM : priority,
                assigneeUserId,
                dueAt);
    }

    /* 在既有 Requirement 下创建带 parent 关系的 UX Task；Agent Tool 与人工入口共用同一防线。 */
    public WorkItem createUxTask(
            long userId,
            long organizationId,
            long requirementId,
            String title,
            String description,
            WorkItemPriority priority,
            Long assigneeUserId) {
        permissionEvaluator.requireOrganization(
                userId, organizationId, WorkItemType.UX_TASK.permissionResource() + ".create");
        WorkItem parent = workItemStore.findByIdAndScope(organizationId, requirementId)
                .orElseThrow(ResourceNotFoundException::new);
        if (parent.type() != WorkItemType.REQUIREMENT) {
            throw new IllegalArgumentException("UX task can only be created under a requirement");
        }
        requireActiveAssignee(organizationId, assigneeUserId);
        return workItemStore.createChild(
                organizationId,
                userId,
                WorkItemType.UX_TASK,
                requirementId,
                normalizeTitle(title),
                normalizeDescription(description),
                WorkItemType.UX_TASK.initialStatus(),
                priority == null ? WorkItemPriority.MEDIUM : priority,
                assigneeUserId,
                null);
    }

    public WorkItem update(
            long userId,
            long organizationId,
            long workItemId,
            String title,
            String description,
            WorkItemPriority priority,
            Long assigneeUserId,
            Instant dueAt,
            long expectedVersion) {
        WorkItem current = workItemStore.findByIdAndScope(organizationId, workItemId)
                .orElseThrow(ResourceNotFoundException::new);
        permissionEvaluator.requireOrganization(
                userId, organizationId, current.type().permissionResource() + ".edit");
        Long resolvedAssignee = assigneeUserId == null ? current.assigneeUserId() : assigneeUserId;
        requireActiveAssignee(organizationId, resolvedAssignee);
        boolean updated = workItemStore.update(
                organizationId,
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
        return workItemStore.findByIdAndScope(organizationId, workItemId).orElseThrow();
    }

    private void requireActiveAssignee(long organizationId, Long assigneeUserId) {
        if (assigneeUserId != null && !organizationStore.hasActiveMember(organizationId, assigneeUserId)) {
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
