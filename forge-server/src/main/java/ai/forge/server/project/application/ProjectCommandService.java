package ai.forge.server.project.application;

import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.project.domain.Project;
import ai.forge.server.workspace.application.WorkspaceAccessService;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class ProjectCommandService {

    /* 最终验证 Workspace Owner 与成员邮箱范围的公开应用服务。 */
    private final WorkspaceAccessService workspaceAccessService;

    /* 提交同一项目事务内项目事实与成员关系的持久化端口。 */
    private final ProjectStore projectStore;

    /* 在项目资源加载后执行最终 RBAC 授权的服务。 */
    private final PermissionEvaluator permissionEvaluator;

    public ProjectCommandService(WorkspaceAccessService workspaceAccessService, ProjectStore projectStore, PermissionEvaluator permissionEvaluator) {
        this.workspaceAccessService = workspaceAccessService;
        this.projectStore = projectStore;
        this.permissionEvaluator = permissionEvaluator;
    }

    public Project create(long userId, long workspaceId, String key, String name, String description) {
        permissionEvaluator.requireWorkspace(userId, workspaceId, "project.manage");
        return projectStore.createWithCreatorMembership(
                workspaceId,
                userId,
                key.trim().toUpperCase(Locale.ROOT),
                name.trim(),
                description == null ? "" : description.trim());
    }

    public void archive(long userId, long workspaceId, long projectId, long expectedVersion) {
        permissionEvaluator.requireProject(userId, workspaceId, projectId, "project.manage");
        Project project = projectStore.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(ResourceNotFoundException::new);
        if (!projectStore.archive(workspaceId, projectId, expectedVersion)) {
            throw new VersionConflictException();
        }
    }

    public void addMember(long userId, long workspaceId, long projectId, String email) {
        permissionEvaluator.requireProject(userId, workspaceId, projectId, "member.manage");
        requireProjectInWorkspace(workspaceId, projectId);
        long memberUserId = workspaceAccessService.requireActiveMemberUserId(
                workspaceId, email.trim().toLowerCase(Locale.ROOT));
        projectStore.activateMember(workspaceId, projectId, memberUserId);
    }

    public void removeMember(long userId, long workspaceId, long projectId, long memberUserId) {
        permissionEvaluator.requireProject(userId, workspaceId, projectId, "member.manage");
        requireProjectInWorkspace(workspaceId, projectId);
        if (userId == memberUserId) {
            throw new IllegalArgumentException("Project owner cannot remove their own membership");
        }
        projectStore.removeMember(workspaceId, projectId, memberUserId);
    }

    private Project requireProjectInWorkspace(long workspaceId, long projectId) {
        return projectStore.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(ResourceNotFoundException::new);
    }
}
