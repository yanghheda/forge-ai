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

    /* 限制领域负责人创建任务及执行人操作范围。 */
    private final DeliveryWorkflowPolicy policy;
    /* 读取任务的需求归属和子任务列表。 */
    private final DeliveryWorkflowStore workflow;

    public WorkItemCommandService(
            PermissionEvaluator permissionEvaluator, OrganizationStore organizationStore, WorkItemStore workItemStore, DeliveryWorkflowPolicy policy, DeliveryWorkflowStore workflow) {
        this.permissionEvaluator = permissionEvaluator;
        this.organizationStore = organizationStore;
        this.workItemStore = workItemStore;
        this.policy = policy;
        this.workflow = workflow;
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
        if (type != WorkItemType.REQUIREMENT) throw new IllegalArgumentException("Tasks must be created under a requirement");
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
    @org.springframework.transaction.annotation.Transactional
    public WorkItem createUxTask(
            long userId,
            long organizationId,
            long requirementId,
            String title,
            String description,
            WorkItemPriority priority,
            Long assigneeUserId) {
        return createTask(userId, organizationId, requirementId, WorkItemType.UX_TASK, title, description, priority, assigneeUserId);
    }

    @org.springframework.transaction.annotation.Transactional
    public WorkItem createTask(long userId, long organizationId, long requirementId, WorkItemType type,
            String title, String description, WorkItemPriority priority, Long assigneeUserId) {
        permissionEvaluator.requireOrganization(userId, organizationId, type.permissionResource() + ".create");
        policy.lock(organizationId, requirementId);
        WorkItem parent = workItemStore.findByIdAndScope(organizationId, requirementId).orElseThrow(ResourceNotFoundException::new);
        policy.requireCreation(userId, parent, type, assigneeUserId);
        return workItemStore.createChild(organizationId, userId, type, requirementId, normalizeTitle(title),
                normalizeDescription(description), type.initialStatus(), priority == null ? WorkItemPriority.MEDIUM : priority,
                assigneeUserId, null);
    }

    public java.util.List<WorkItem> tasks(long userId, long organizationId, long requirementId) {
        permissionEvaluator.requireOrganization(userId, organizationId, "requirement.read");
        workItemStore.findByIdAndScope(organizationId, requirementId).filter(w -> w.type() == WorkItemType.REQUIREMENT)
                .orElseThrow(ResourceNotFoundException::new);
        return workflow.taskIds(organizationId, requirementId).stream()
                .map(id -> workItemStore.findByIdAndScope(organizationId, id).orElseThrow(ResourceNotFoundException::new)).toList();
    }

    @org.springframework.transaction.annotation.Transactional
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
        long requirementId = policy.lock(organizationId, workItemId);
        WorkItem current = workItemStore.findByIdAndScope(organizationId, workItemId)
                .orElseThrow(ResourceNotFoundException::new);
        permissionEvaluator.requireOrganization(
                userId, organizationId, current.type().permissionResource() + ".edit");
        Long resolvedAssignee = assigneeUserId == null ? current.assigneeUserId() : assigneeUserId;
        if (current.type() != WorkItemType.REQUIREMENT) {
            if (!policy.leader(organizationId, requirementId, DeliveryWorkflowPolicy.role(current.type()), userId)
                    && !(current.type() == WorkItemType.BUG && policy.member(organizationId, requirementId, "QA", userId)))
                throw new ResourceNotFoundException();
            policy.requireAssignee(organizationId, requirementId, current.type(), resolvedAssignee);
        }
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
