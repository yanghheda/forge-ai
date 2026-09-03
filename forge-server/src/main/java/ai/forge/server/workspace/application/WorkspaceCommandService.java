package ai.forge.server.workspace.application;

import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.workspace.domain.Workspace;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class WorkspaceCommandService {

    /* 检查创建者是否保有初始化阶段已有的 Owner 管理范围。 */
    private final WorkspaceStore workspaceStore;

    /* 验证成员管理请求的当前 Workspace Owner 范围。 */
    private final WorkspaceAccessService workspaceAccessService;

    public WorkspaceCommandService(WorkspaceStore workspaceStore, WorkspaceAccessService workspaceAccessService) {
        this.workspaceStore = workspaceStore;
        this.workspaceAccessService = workspaceAccessService;
    }

    public Workspace create(long userId, String name, String slug) {
        if (!workspaceStore.hasAnyActiveOwnerRole(userId)) {
            throw new ResourceNotFoundException();
        }
        return workspaceStore.createForOwner(userId, name.trim(), slug.trim().toLowerCase(Locale.ROOT));
    }

    public void addMember(long userId, long workspaceId, String email) {
        workspaceAccessService.requireOwner(userId, workspaceId);
        long memberUserId = workspaceAccessService.requireActiveUserId(email.trim().toLowerCase(Locale.ROOT));
        workspaceStore.activateMember(workspaceId, memberUserId);
    }

    public void removeMember(long userId, long workspaceId, long memberUserId) {
        workspaceAccessService.requireOwner(userId, workspaceId);
        if (userId == memberUserId) {
            throw new IllegalArgumentException("Workspace owner cannot remove their own membership");
        }
        workspaceStore.removeMember(workspaceId, memberUserId);
    }
}
