package ai.forge.server.workitem.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.notification.application.NotificationDraft;
import ai.forge.server.notification.application.NotificationService;
import ai.forge.server.notification.domain.NotificationTarget;
import ai.forge.server.notification.domain.NotificationType;
import ai.forge.server.organization.application.OrganizationStore;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemType;
import java.time.Instant;
import java.util.List;
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

    /* 负责人被创建指派或改派时，在业务事务内写入站内指派通知。 */
    private final NotificationService notificationService;

    public WorkItemCommandService(
            PermissionEvaluator permissionEvaluator, OrganizationStore organizationStore, WorkItemStore workItemStore, DeliveryWorkflowPolicy policy, DeliveryWorkflowStore workflow, NotificationService notificationService) {
        this.permissionEvaluator = permissionEvaluator;
        this.organizationStore = organizationStore;
        this.workItemStore = workItemStore;
        this.policy = policy;
        this.workflow = workflow;
        this.notificationService = notificationService;
    }

    /* 创建需求成功后写入指派通知，必须与工作项写入处于同一事务以保证整体提交或回滚。 */
    @org.springframework.transaction.annotation.Transactional
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
        WorkItem created = workItemStore.create(
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
        /* 需求创建即带负责人时通知该负责人；需求是交付链根节点，跳转资源为自身。 */
        notifyAssignee(organizationId, userId, assigneeUserId, created, created.id());
        return created;
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
        WorkItem created = workItemStore.createChild(organizationId, userId, type, requirementId, normalizeTitle(title),
                normalizeDescription(description), type.initialStatus(), priority == null ? WorkItemPriority.MEDIUM : priority,
                assigneeUserId, null);
        /* 任务或缺陷创建即带负责人时通知该负责人；任务跳转到父需求阶段页，缺陷跳转到自身详情页。 */
        notifyAssignee(organizationId, userId, assigneeUserId, created, requirementId);
        return created;
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
        WorkItem latest = workItemStore.findByIdAndScope(organizationId, workItemId).orElseThrow();
        /* 仅在负责人实际发生变更时通知新负责人；重复保存同一负责人或未指派不写入。 */
        if (resolvedAssignee != null && !resolvedAssignee.equals(current.assigneeUserId())) {
            notifyAssignee(organizationId, userId, resolvedAssignee, latest, requirementId);
        }
        return latest;
    }

    /*
     * 负责人被设置或改派为他人时写入 WORK_ITEM_ASSIGNED 通知：收件人为新负责人，
     * 操作者由 NotificationService 自动排除（自指派不产生通知）；跳转目标按工作项类型映射。
     */
    private void notifyAssignee(long organizationId, long actorUserId, Long assigneeUserId, WorkItem item, long rootRequirementId) {
        if (assigneeUserId == null) return;
        notificationService.notify(new NotificationDraft(
                organizationId,
                List.of(assigneeUserId),
                actorUserId,
                NotificationType.WORK_ITEM_ASSIGNED,
                "新指派：" + item.itemKey() + " " + item.title(),
                "你是该" + assignmentLabel(item.type()) + "的负责人，请及时处理。",
                assignmentTarget(item.type()),
                Long.toString(assignmentResourceId(item, rootRequirementId))));
    }

    /* 指派通知的跳转目标按工作项类型映射到对应阶段页或缺陷详情页。 */
    private static NotificationTarget assignmentTarget(WorkItemType type) {
        return switch (type) {
            case REQUIREMENT -> NotificationTarget.REQUIREMENT;
            case UX_TASK -> NotificationTarget.REQUIREMENT_UX;
            case DEV_TASK -> NotificationTarget.REQUIREMENT_DEVELOPMENT;
            case QA_TASK -> NotificationTarget.REQUIREMENT_QA;
            case BUG -> NotificationTarget.DEFECT;
        };
    }

    /* 任务类跳转到父需求，需求与缺陷跳转到自身；用于拼装 resource_id。 */
    private static long assignmentResourceId(WorkItem item, long rootRequirementId) {
        return item.type() == WorkItemType.REQUIREMENT || item.type() == WorkItemType.BUG
                ? item.id()
                : rootRequirementId;
    }

    /* 指派通知正文中的类型中文名，与前端阶段页文案保持一致。 */
    private static String assignmentLabel(WorkItemType type) {
        return switch (type) {
            case REQUIREMENT -> "需求";
            case UX_TASK -> "UX 任务";
            case DEV_TASK -> "开发任务";
            case QA_TASK -> "测试任务";
            case BUG -> "缺陷";
        };
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
