package ai.forge.server.workitem.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.notification.application.NotificationDraft;
import ai.forge.server.notification.application.NotificationService;
import ai.forge.server.notification.domain.NotificationTarget;
import ai.forge.server.notification.domain.NotificationType;
import ai.forge.server.organization.application.OrganizationAccessService;
import ai.forge.server.organization.domain.OrganizationContext;
import ai.forge.server.workitem.domain.RequirementParticipantRole;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test-unit")
public class OrganizationRequirementService {

    /* 从当前用户解析服务端唯一默认组织 scope。 */
    private final OrganizationAccessService accessService;

    /* 复用既有原子编号、字段规范化和创建授权。 */
    private final WorkItemCommandService commandService;

    /* 复用既有按实际资源类型执行的详情授权。 */
    private final WorkItemQueryService queryService;

    /* 对参与人设置和需求列表执行服务端最终权限校验。 */
    private final PermissionEvaluator permissionEvaluator;

    /* 承载组织需求列表、统计和角色参与人持久化。 */
    private final OrganizationRequirementStore store;

    /* 串行化需求人员调整，并校验领域负责人身份。 */
    private final DeliveryWorkflowStore workflow;

    /* 需求人员被净新增关联为领域负责人或执行人时，在业务事务内写入站内指派通知。 */
    private final NotificationService notificationService;

    public OrganizationRequirementService(
            OrganizationAccessService accessService,
            WorkItemCommandService commandService,
            WorkItemQueryService queryService,
            PermissionEvaluator permissionEvaluator,
            OrganizationRequirementStore store, DeliveryWorkflowStore workflow,
            NotificationService notificationService) {
        this.accessService = accessService;
        this.commandService = commandService;
        this.queryService = queryService;
        this.permissionEvaluator = permissionEvaluator;
        this.store = store;
        this.workflow = workflow;
        this.notificationService = notificationService;
    }

    @Transactional
    public OrganizationRequirementView create(
            long userId, String title, String description, WorkItemPriority priority) {
        OrganizationContext scope = accessService.requireContext(userId);
        WorkItem item = commandService.create(
                userId,
                scope.organizationId(),
                WorkItemType.REQUIREMENT,
                title,
                description,
                priority,
                null,
                null);
        if (store.memberCanFillRole(
                scope.organizationId(), userId, RequirementParticipantRole.PRODUCT)) {
            store.replaceParticipants(
                    scope.organizationId(),
                    item.id(),
                    userId,
                    List.of(new OrganizationRequirementStore.ParticipantAssignment(
                            RequirementParticipantRole.PRODUCT, userId)));
        }
        return store.findRequirement(scope.organizationId(), item.id()).orElseThrow();
    }

    public OrganizationRequirementView get(long userId, long requirementId) {
        OrganizationContext scope = accessService.requireContext(userId);
        WorkItem item = queryService.get(userId, scope.organizationId(), requirementId);
        return store.findRequirement(scope.organizationId(), item.id()).orElseThrow();
    }

    @Transactional
    public void delete(long userId, long requirementId, long expectedVersion) {
        OrganizationContext scope = accessService.requireContext(userId);
        permissionEvaluator.requireOrganization(userId, scope.organizationId(), "requirement.edit");
        if (workflow.lockRequirement(scope.organizationId(), requirementId) == null)
            throw new ai.forge.server.common.domain.ResourceNotFoundException();
        WorkItem item = queryService.get(userId, scope.organizationId(), requirementId);
        if (item.type() != WorkItemType.REQUIREMENT)
            throw new ai.forge.server.common.domain.ResourceNotFoundException();
        if (item.status() != WorkItemStatus.DRAFT)
            throw new ai.forge.server.workitem.domain.InvalidTransitionException();
        if (!store.softDelete(scope.organizationId(), requirementId, expectedVersion))
            throw new ai.forge.server.common.domain.VersionConflictException();
    }

    public OrganizationRequirementPage list(
            long userId, boolean mine, String query, WorkItemStatus status, int page, int pageSize) {
        OrganizationContext scope = accessService.requireContext(userId);
        permissionEvaluator.requireOrganization(
                userId, scope.organizationId(), "requirement.read");
        Long participantUserId = mine && !scope.owner() ? userId : null;
        return store.findRequirements(
                scope.organizationId(),
                participantUserId,
                query == null ? "" : query.trim(),
                status,
                page,
                pageSize);
    }

    public RequirementOverview overview(long userId) {
        OrganizationContext scope = accessService.requireContext(userId);
        permissionEvaluator.requireOrganization(
                userId, scope.organizationId(), "requirement.read");
        return store.overview(scope.organizationId());
    }

    public List<RequirementParticipantView> participants(long userId, long requirementId) {
        OrganizationContext scope = accessService.requireContext(userId);
        requireRequirement(userId, scope, requirementId);
        return store.findParticipants(scope.organizationId(), requirementId);
    }

    @Transactional
    public List<RequirementParticipantView> replaceParticipants(
            long userId,
            long requirementId,
            List<OrganizationRequirementStore.ParticipantAssignment> assignments) {
        OrganizationContext scope = accessService.requireContext(userId);
        WorkItem requirement = lockEditable(userId, scope, requirementId);
        permissionEvaluator.requireOrganization(userId, scope.organizationId(), "requirement.edit");
        if (requirement.reporterUserId() != userId && !workflow.isLeader(scope.organizationId(), requirementId, "PRODUCT", userId)) {
            throw new ai.forge.server.common.domain.ResourceNotFoundException();
        }
        if (assignments.size() > RequirementParticipantRole.values().length
                || new HashSet<>(assignments.stream().map(OrganizationRequirementStore.ParticipantAssignment::role).toList())
                        .size() != assignments.size()) {
            throw new IllegalArgumentException("Requirement participant roles must be unique");
        }
        for (OrganizationRequirementStore.ParticipantAssignment assignment : assignments) {
            if (!store.memberCanFillRole(
                    scope.organizationId(), assignment.userId(), assignment.role())) {
                throw new IllegalArgumentException("Member does not hold the selected requirement role");
            }
        }
        /* 替换前只读一次负责人快照，同时支撑「负责人变化 → 清空该角色执行人」与净新增通知判定。 */
        List<RequirementParticipantView> previous = store.findParticipants(scope.organizationId(), requirementId);
        Map<RequirementParticipantRole, Long> previousByRole = new HashMap<>();
        for (RequirementParticipantView view : previous) previousByRole.putIfAbsent(view.role(), view.userId());
        for (RequirementParticipantRole role : RequirementParticipantRole.values()) {
            Long previousUser = previousByRole.get(role);
            Long next = assignments.stream().filter(p -> p.role() == role).map(OrganizationRequirementStore.ParticipantAssignment::userId).findFirst().orElse(null);
            if (!java.util.Objects.equals(previousUser, next)) workflow.deleteExecutors(scope.organizationId(), requirementId, role.name());
        }
        workflow.clearVotes(scope.organizationId(), requirementId);
        workflow.touchRequirement(scope.organizationId(), requirementId);
        List<RequirementParticipantView> result = store.replaceParticipants(scope.organizationId(), requirementId, userId, assignments);
        /* 仅对净新增（含更换后）的领域负责人通知；未变化或移除的负责人不产生通知。 */
        for (OrganizationRequirementStore.ParticipantAssignment assignment : assignments) {
            if (!java.util.Objects.equals(previousByRole.get(assignment.role()), assignment.userId())) {
                notifyAssociation(scope.organizationId(), userId, requirementId, requirement,
                        List.of(assignment.userId()), assignment.role(), "负责人");
            }
        }
        return result;
    }

    @Transactional
    public List<RequirementParticipantView> replaceExecutors(long userId, long requirementId,
            RequirementParticipantRole role, List<Long> userIds) {
        OrganizationContext scope = accessService.requireContext(userId);
        WorkItem requirement = lockEditable(userId, scope, requirementId);
        if (!workflow.isLeader(scope.organizationId(), requirementId, role.name(), userId))
            throw new ai.forge.server.common.domain.ResourceNotFoundException();
        if (new HashSet<>(userIds).size() != userIds.size()) throw new IllegalArgumentException("Executors must be unique");
        for (Long executor : userIds) {
            if (!store.memberHasRole(scope.organizationId(), executor, role))
                throw new IllegalArgumentException("Executor must hold the corresponding domain role");
        }
        /* 删除前读取既有执行人快照，用于判定净新增。 */
        List<Long> previous = workflow.executors(scope.organizationId(), requirementId, role.name());
        workflow.deleteExecutors(scope.organizationId(), requirementId, role.name());
        for (Long executor : userIds) workflow.addExecutor(scope.organizationId(), requirementId, role.name(), executor, userId);
        workflow.clearVotes(scope.organizationId(), requirementId);
        workflow.touchRequirement(scope.organizationId(), requirementId);
        List<RequirementParticipantView> result = store.findParticipants(scope.organizationId(), requirementId);
        /* 仅对净新增执行人一次性通知；未变化或被移除的执行人不产生通知。 */
        List<Long> added = userIds.stream().filter(executor -> !previous.contains(executor)).toList();
        notifyAssociation(scope.organizationId(), userId, requirementId, requirement, added, role, "执行人");
        return result;
    }

    /*
     * 将净新增的领域负责人或执行人写入 WORK_ITEM_ASSIGNED 通知：收件人为空时不写入，
     * 操作者由 NotificationService 自动排除（创建人自登记为产品负责人等自关联不产生通知）。
     */
    private void notifyAssociation(long organizationId, long actorUserId, long requirementId, WorkItem requirement,
            List<Long> recipients, RequirementParticipantRole role, String responsibility) {
        if (recipients.isEmpty()) return;
        notificationService.notify(new NotificationDraft(
                organizationId,
                recipients,
                actorUserId,
                NotificationType.WORK_ITEM_ASSIGNED,
                "新指派：" + requirement.itemKey() + " " + requirement.title(),
                "你被关联为需求 " + requirement.itemKey() + " 的" + roleLabel(role) + responsibility + "，请及时处理。",
                NotificationTarget.REQUIREMENT,
                String.valueOf(requirementId)));
    }

    /* 关联通知正文使用的领域中文名，与需求协作人设置界面保持一致。 */
    private static String roleLabel(RequirementParticipantRole role) {
        return switch (role) {
            case PRODUCT -> "产品";
            case UX -> "UX";
            case DEVELOPER -> "开发";
            case QA -> "测试";
        };
    }

    private WorkItem lockEditable(long userId, OrganizationContext scope, long requirementId) {
        if (workflow.lockRequirement(scope.organizationId(), requirementId) == null)
            throw new ai.forge.server.common.domain.ResourceNotFoundException();
        WorkItem item = queryService.get(userId, scope.organizationId(), requirementId);
        if (item.status() != WorkItemStatus.DRAFT && item.status() != WorkItemStatus.REQUIREMENT_APPROVAL
                && item.status() != WorkItemStatus.READY_FOR_REVIEW) throw new ai.forge.server.workitem.domain.InvalidTransitionException();
        return item;
    }

    public List<RequirementMemberView> members(long userId) {
        OrganizationContext scope = accessService.requireContext(userId);
        permissionEvaluator.requireOrganization(
                userId, scope.organizationId(), "requirement.read");
        return store.findMembers(scope.organizationId());
    }

    private void requireRequirement(long userId, OrganizationContext scope, long requirementId) {
        WorkItem item = queryService.get(userId, scope.organizationId(), requirementId);
        if (item.type() != WorkItemType.REQUIREMENT) {
            throw new IllegalArgumentException("Resource is not a requirement");
        }
    }

}
