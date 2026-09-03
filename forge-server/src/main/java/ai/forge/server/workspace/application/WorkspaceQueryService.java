package ai.forge.server.workspace.application;

import ai.forge.server.workspace.domain.Workspace;
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

    public WorkspaceQueryService(WorkspaceAccessService workspaceAccessService, WorkspaceStore workspaceStore) {
        this.workspaceAccessService = workspaceAccessService;
        this.workspaceStore = workspaceStore;
    }

    public List<Workspace> list(long userId) {
        return workspaceStore.findActiveByUserId(userId);
    }

    public Workspace getBySlug(long userId, String slug) {
        return workspaceAccessService.requireMemberBySlug(userId, slug);
    }

    public List<WorkspaceMember> members(long userId, long workspaceId) {
        workspaceAccessService.requireOwner(userId, workspaceId);
        return workspaceStore.findMembersByWorkspaceId(workspaceId);
    }
}
