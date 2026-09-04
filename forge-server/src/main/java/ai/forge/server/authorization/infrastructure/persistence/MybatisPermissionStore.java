package ai.forge.server.authorization.infrastructure.persistence;

import ai.forge.server.authorization.application.PermissionStore;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test-unit")
public class MybatisPermissionStore implements PermissionStore {

    /* 使用显式 scope SQL 读取 RBAC 事实的 MyBatis Mapper。 */
    private final PermissionMapper permissionMapper;

    public MybatisPermissionStore(PermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findWorkspacePermissionSet(long userId, long workspaceId) {
        if (!permissionMapper.activeWorkspaceMember(userId, workspaceId)) {
            return Set.of();
        }
        return new LinkedHashSet<>(permissionMapper.workspacePermissions(userId, workspaceId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectAccess> findProjectAccess(long userId, long workspaceId, long projectId) {
        if (!permissionMapper.projectExists(projectId, workspaceId)
                || !permissionMapper.activeWorkspaceMember(userId, workspaceId)) {
            return Optional.empty();
        }
        return Optional.of(new ProjectAccess(
                permissionMapper.activeProjectMember(userId, workspaceId, projectId),
                new LinkedHashSet<>(permissionMapper.projectRoles(userId, workspaceId, projectId)),
                new LinkedHashSet<>(permissionMapper.projectPermissions(userId, workspaceId, projectId))));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findProjectIdsWithPermission(long userId, long workspaceId, String permission) {
        if (!permissionMapper.activeWorkspaceMember(userId, workspaceId)) {
            return List.of();
        }
        return permissionMapper.projectIdsWithPermission(userId, workspaceId, permission);
    }
}
