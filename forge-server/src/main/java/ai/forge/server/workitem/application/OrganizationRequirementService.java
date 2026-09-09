package ai.forge.server.workitem.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.workitem.domain.RequirementParticipantRole;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import ai.forge.server.workspace.application.WorkspaceAccessService;
import ai.forge.server.workspace.domain.OrganizationScope;
import java.util.HashSet;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class OrganizationRequirementService {

    /* 从当前用户解析服务端唯一默认组织 scope。 */
    private final WorkspaceAccessService accessService;

    /* 复用既有原子编号、字段规范化和创建授权。 */
    private final WorkItemCommandService commandService;

    /* 复用既有按实际资源类型执行的详情授权。 */
    private final WorkItemQueryService queryService;

    /* 对参与人设置和需求列表执行服务端最终权限校验。 */
    private final PermissionEvaluator permissionEvaluator;

    /* 承载组织需求列表、统计和角色参与人持久化。 */
    private final OrganizationRequirementStore store;

    public OrganizationRequirementService(
            WorkspaceAccessService accessService,
            WorkItemCommandService commandService,
            WorkItemQueryService queryService,
            PermissionEvaluator permissionEvaluator,
            OrganizationRequirementStore store) {
        this.accessService = accessService;
        this.commandService = commandService;
        this.queryService = queryService;
        this.permissionEvaluator = permissionEvaluator;
        this.store = store;
    }

    public OrganizationRequirementView create(
            long userId, String title, String description, WorkItemPriority priority) {
        OrganizationScope scope = accessService.requireDefaultScope(userId);
        WorkItem item = commandService.create(
                userId,
                scope.workspaceId(),
                scope.projectId(),
                WorkItemType.REQUIREMENT,
                title,
                description,
                priority,
                null,
                null);
        return view(item);
    }

    public OrganizationRequirementView get(long userId, long requirementId) {
        OrganizationScope scope = accessService.requireDefaultScope(userId);
        return view(queryService.get(userId, scope.workspaceId(), scope.projectId(), requirementId));
    }

    public OrganizationRequirementPage list(
            long userId, boolean mine, String query, WorkItemStatus status, int page, int pageSize) {
        OrganizationScope scope = accessService.requireDefaultScope(userId);
        permissionEvaluator.requireProject(
                userId, scope.workspaceId(), scope.projectId(), "requirement.read");
        Long participantUserId = mine && !scope.owner() ? userId : null;
        return store.findRequirements(
                scope.workspaceId(),
                scope.projectId(),
                participantUserId,
                query == null ? "" : query.trim(),
                status,
                page,
                pageSize);
    }

    public RequirementOverview overview(long userId) {
        OrganizationScope scope = accessService.requireDefaultScope(userId);
        permissionEvaluator.requireProject(
                userId, scope.workspaceId(), scope.projectId(), "requirement.read");
        return store.overview(scope.workspaceId(), scope.projectId());
    }

    public List<RequirementParticipantView> participants(long userId, long requirementId) {
        OrganizationScope scope = accessService.requireDefaultScope(userId);
        requireRequirement(userId, scope, requirementId);
        return store.findParticipants(scope.workspaceId(), scope.projectId(), requirementId);
    }

    public List<RequirementParticipantView> replaceParticipants(
            long userId,
            long requirementId,
            List<OrganizationRequirementStore.ParticipantAssignment> assignments) {
        OrganizationScope scope = accessService.requireDefaultScope(userId);
        requireRequirement(userId, scope, requirementId);
        permissionEvaluator.requireProject(
                userId, scope.workspaceId(), scope.projectId(), "requirement.edit");
        if (assignments.size() > RequirementParticipantRole.values().length
                || new HashSet<>(assignments.stream().map(OrganizationRequirementStore.ParticipantAssignment::role).toList())
                        .size() != assignments.size()) {
            throw new IllegalArgumentException("Requirement participant roles must be unique");
        }
        for (OrganizationRequirementStore.ParticipantAssignment assignment : assignments) {
            if (!store.memberCanFillRole(
                    scope.workspaceId(), scope.projectId(), assignment.userId(), assignment.role())) {
                throw new IllegalArgumentException("Member does not hold the selected requirement role");
            }
        }
        return store.replaceParticipants(
                scope.workspaceId(), scope.projectId(), requirementId, userId, assignments);
    }

    public List<RequirementMemberView> members(long userId) {
        OrganizationScope scope = accessService.requireDefaultScope(userId);
        permissionEvaluator.requireProject(
                userId, scope.workspaceId(), scope.projectId(), "requirement.read");
        return store.findMembers(scope.workspaceId(), scope.projectId());
    }

    private void requireRequirement(long userId, OrganizationScope scope, long requirementId) {
        WorkItem item = queryService.get(userId, scope.workspaceId(), scope.projectId(), requirementId);
        if (item.type() != WorkItemType.REQUIREMENT) {
            throw new IllegalArgumentException("Resource is not a requirement");
        }
    }

    private OrganizationRequirementView view(WorkItem item) {
        return new OrganizationRequirementView(
                item.id(),
                item.itemKey(),
                item.title(),
                item.description(),
                item.status(),
                item.priority(),
                item.version(),
                item.createdAt(),
                item.updatedAt());
    }
}
