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

    public void requireOrganization(long userId, long organizationId, String permission) {
        if (!hasOrganizationPermission(userId, organizationId, permission)) {
            throw new ResourceNotFoundException();
        }
    }

    public boolean hasOrganizationPermission(long userId, long organizationId, String permission) {
        return permissionStore.findOrganizationPermissionSet(userId, organizationId).contains(permission);
    }

    public Set<String> organizationPermissions(long userId, long organizationId) {
        return permissionStore.findOrganizationPermissionSet(userId, organizationId);
    }
}
