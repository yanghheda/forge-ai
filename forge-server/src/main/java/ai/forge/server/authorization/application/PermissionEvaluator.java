package ai.forge.server.authorization.application;

import ai.forge.server.common.domain.ResourceNotFoundException;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class PermissionEvaluator {

    /* 读取成员版本、资源范围和角色权限的持久化端口。 */
    private final PermissionStore permissionStore;

    public PermissionEvaluator(PermissionStore permissionStore) {
        this.permissionStore = permissionStore;
    }

    public void requireWorkspace(long userId, long workspaceId, String permission) {
        if (!hasWorkspacePermission(userId, workspaceId, permission)) {
            throw new ResourceNotFoundException();
        }
    }

    public void requireProject(long userId, long workspaceId, long projectId, String permission) {
        if (!hasProjectPermission(userId, workspaceId, projectId, permission)) {
            throw new ResourceNotFoundException();
        }
    }

    public boolean hasWorkspacePermission(long userId, long workspaceId, String permission) {
        return permissionStore.findWorkspacePermissionSet(userId, workspaceId).contains(permission);
    }

    public boolean hasProjectPermission(long userId, long workspaceId, long projectId, String permission) {
        PermissionStore.ProjectAccess access = permissionStore.findProjectAccess(userId, workspaceId, projectId)
                .orElseThrow(ResourceNotFoundException::new);
        if (!access.projectMember() && !access.roleCodes().contains("OWNER") && !access.roleCodes().contains("ADMIN")) {
            return false;
        }
        return access.permissions().contains(permission);
    }

    public Set<String> workspacePermissions(long userId, long workspaceId) {
        return permissionStore.findWorkspacePermissionSet(userId, workspaceId);
    }
}
