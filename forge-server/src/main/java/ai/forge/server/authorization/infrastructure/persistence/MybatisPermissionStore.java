package ai.forge.server.authorization.infrastructure.persistence;

import ai.forge.server.authorization.application.PermissionStore;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test-unit")
public class MybatisPermissionStore implements PermissionStore {

    /* 使用显式公司范围 SQL 读取 RBAC 事实的 MyBatis Mapper。 */
    private final PermissionMapper permissionMapper;

    public MybatisPermissionStore(PermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findOrganizationPermissionSet(long userId, long organizationId) {
        return new LinkedHashSet<>(permissionMapper.organizationPermissions(userId, organizationId));
    }
}
