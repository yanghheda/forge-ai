package ai.forge.server.workspace.application;

import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.authorization.application.PermissionEvaluator;
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

    /* 在写入成员事实前执行最终 RBAC 授权的服务。 */
    private final PermissionEvaluator permissionEvaluator;

    public WorkspaceCommandService(WorkspaceStore workspaceStore, WorkspaceAccessService workspaceAccessService, PermissionEvaluator permissionEvaluator) {
        this.workspaceStore = workspaceStore;
        this.workspaceAccessService = workspaceAccessService;
        this.permissionEvaluator = permissionEvaluator;
    }

    public Workspace create(long userId, String name, String slug) {
        if (!workspaceStore.hasAnyActiveOwnerRole(userId)) {
            throw new ResourceNotFoundException();
        }
        return workspaceStore.createForOwner(userId, name.trim(), slug.trim().toLowerCase(Locale.ROOT));
    }

    public void addMember(long userId, long workspaceId, String email) {
        permissionEvaluator.requireWorkspace(userId, workspaceId, "member.manage");
        long memberUserId = workspaceAccessService.requireActiveUserId(email.trim().toLowerCase(Locale.ROOT));
        workspaceStore.activateMember(workspaceId, memberUserId);
    }

    public void removeMember(long userId, long workspaceId, long memberUserId) {
        permissionEvaluator.requireWorkspace(userId, workspaceId, "member.manage");
        if (userId == memberUserId) {
            throw new IllegalArgumentException("Workspace owner cannot remove their own membership");
        }
        workspaceStore.removeMember(workspaceId, memberUserId);
    }
}
