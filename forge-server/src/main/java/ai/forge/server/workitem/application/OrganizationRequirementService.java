package ai.forge.server.workitem.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.organization.application.OrganizationAccessService;
import ai.forge.server.organization.domain.OrganizationContext;
import ai.forge.server.workitem.domain.RequirementParticipantRole;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import java.util.HashSet;
import java.util.List;
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

    public OrganizationRequirementService(
            OrganizationAccessService accessService,
            WorkItemCommandService commandService,
            WorkItemQueryService queryService,
            PermissionEvaluator permissionEvaluator,
            OrganizationRequirementStore store, DeliveryWorkflowStore workflow) {
        this.accessService = accessService;
        this.commandService = commandService;
        this.queryService = queryService;
        this.permissionEvaluator = permissionEvaluator;
        this.store = store;
        this.workflow = workflow;
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
        for (RequirementParticipantRole role : RequirementParticipantRole.values()) {
            Long previous = store.findParticipants(scope.organizationId(), requirementId).stream()
                    .filter(p -> p.role() == role).map(RequirementParticipantView::userId).findFirst().orElse(null);
            Long next = assignments.stream().filter(p -> p.role() == role).map(OrganizationRequirementStore.ParticipantAssignment::userId).findFirst().orElse(null);
            if (!java.util.Objects.equals(previous, next)) workflow.deleteExecutors(scope.organizationId(), requirementId, role.name());
        }
        workflow.clearVotes(scope.organizationId(), requirementId);
        workflow.touchRequirement(scope.organizationId(), requirementId);
        return store.replaceParticipants(scope.organizationId(), requirementId, userId, assignments);
    }

    @Transactional
    public List<RequirementParticipantView> replaceExecutors(long userId, long requirementId,
            RequirementParticipantRole role, List<Long> userIds) {
        OrganizationContext scope = accessService.requireContext(userId);
        lockEditable(userId, scope, requirementId);
        if (!workflow.isLeader(scope.organizationId(), requirementId, role.name(), userId))
            throw new ai.forge.server.common.domain.ResourceNotFoundException();
        if (new HashSet<>(userIds).size() != userIds.size()) throw new IllegalArgumentException("Executors must be unique");
        for (Long executor : userIds) {
            if (!store.memberHasRole(scope.organizationId(), executor, role))
                throw new IllegalArgumentException("Executor must hold the corresponding domain role");
        }
        workflow.deleteExecutors(scope.organizationId(), requirementId, role.name());
        for (Long executor : userIds) workflow.addExecutor(scope.organizationId(), requirementId, role.name(), executor, userId);
        workflow.clearVotes(scope.organizationId(), requirementId);
        workflow.touchRequirement(scope.organizationId(), requirementId);
        return store.findParticipants(scope.organizationId(), requirementId);
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
