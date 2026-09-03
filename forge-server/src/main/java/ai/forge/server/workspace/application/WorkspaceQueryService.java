package ai.forge.server.workspace.application;

import ai.forge.server.workspace.domain.Workspace;
import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.workspace.domain.WorkspaceMember;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class WorkspaceQueryService {

    /* 用于验证范围并读取 Workspace 与成员摘要的应用服务。 */
    private final WorkspaceAccessService workspaceAccessService;

    /* 返回 Workspace 公开摘要和成员目录的持久化端口。 */
    private final WorkspaceStore workspaceStore;

    /* 在返回成员目录前执行最终 RBAC 授权的服务。 */
    private final PermissionEvaluator permissionEvaluator;

    public WorkspaceQueryService(WorkspaceAccessService workspaceAccessService, WorkspaceStore workspaceStore, PermissionEvaluator permissionEvaluator) {
        this.workspaceAccessService = workspaceAccessService;
        this.workspaceStore = workspaceStore;
        this.permissionEvaluator = permissionEvaluator;
    }

    public List<Workspace> list(long userId) {
        return workspaceStore.findActiveByUserId(userId);
    }

    public Workspace getBySlug(long userId, String slug) {
        return workspaceAccessService.requireMemberBySlug(userId, slug);
    }

    public List<WorkspaceMember> members(long userId, long workspaceId) {
        permissionEvaluator.requireWorkspace(userId, workspaceId, "member.read");
        return workspaceStore.findMembersByWorkspaceId(workspaceId);
    }
}
