package ai.forge.server.project.application;

import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.project.domain.Project;
import ai.forge.server.project.domain.ProjectMember;
import ai.forge.server.workspace.application.WorkspaceAccessService;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class ProjectQueryService {

    /* 验证 Workspace 成员关系与初始 Owner 管理范围的公开应用服务。 */
    private final WorkspaceAccessService workspaceAccessService;

    /* 只接受 Workspace 范围参数的项目持久化端口。 */
    private final ProjectStore projectStore;

    /* 在项目事实加载后验证角色权限的授权服务。 */
    private final PermissionEvaluator permissionEvaluator;

    public ProjectQueryService(WorkspaceAccessService workspaceAccessService, ProjectStore projectStore, PermissionEvaluator permissionEvaluator) {
        this.workspaceAccessService = workspaceAccessService;
        this.projectStore = projectStore;
        this.permissionEvaluator = permissionEvaluator;
    }

    public List<Project> list(long userId, long workspaceId) {
        workspaceAccessService.requireMember(userId, workspaceId);
        return projectStore.findByWorkspaceId(workspaceId).stream()
                .filter(project -> permissionEvaluator.hasProjectPermission(userId, workspaceId, project.id(), "project.read"))
                .toList();
    }

    public Project get(long userId, long workspaceId, long projectId) {
        workspaceAccessService.requireMember(userId, workspaceId);
        Project project = projectStore.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(ResourceNotFoundException::new);
        permissionEvaluator.requireProject(userId, workspaceId, project.id(), "project.read");
        return project;
    }

    public List<ProjectMember> members(long userId, long workspaceId, long projectId) {
        permissionEvaluator.requireProject(userId, workspaceId, projectId, "member.read");
        return projectStore.findMembersByProjectIdAndWorkspaceId(projectId, workspaceId);
    }

    private Project requireProjectInWorkspace(long workspaceId, long projectId) {
        return projectStore.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(ResourceNotFoundException::new);
    }
}
