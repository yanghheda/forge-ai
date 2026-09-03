package ai.forge.server.project.application;

import ai.forge.server.common.domain.ResourceNotFoundException;
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

    public ProjectQueryService(WorkspaceAccessService workspaceAccessService, ProjectStore projectStore) {
        this.workspaceAccessService = workspaceAccessService;
        this.projectStore = projectStore;
    }

    public List<Project> list(long userId, long workspaceId) {
        workspaceAccessService.requireMember(userId, workspaceId);
        return projectStore.findByWorkspaceId(workspaceId).stream()
                .filter(project -> canAccess(userId, workspaceId, project.id()))
                .toList();
    }

    public Project get(long userId, long workspaceId, long projectId) {
        workspaceAccessService.requireMember(userId, workspaceId);
        Project project = projectStore.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(ResourceNotFoundException::new);
        if (!canAccess(userId, workspaceId, project.id())) {
            throw new ResourceNotFoundException();
        }
        return project;
    }

    public List<ProjectMember> members(long userId, long workspaceId, long projectId) {
        workspaceAccessService.requireOwner(userId, workspaceId);
        requireProjectInWorkspace(workspaceId, projectId);
        return projectStore.findMembersByProjectIdAndWorkspaceId(projectId, workspaceId);
    }

    private boolean canAccess(long userId, long workspaceId, long projectId) {
        return workspaceAccessService.isOwner(userId, workspaceId)
                || projectStore.hasActiveMember(workspaceId, projectId, userId);
    }

    private Project requireProjectInWorkspace(long workspaceId, long projectId) {
        return projectStore.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(ResourceNotFoundException::new);
    }
}
