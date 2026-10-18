package ai.forge.server.organization.application;

import ai.forge.server.organization.domain.OrganizationContext;
import ai.forge.server.organization.domain.OrganizationMember;
import java.util.List;
import java.util.Optional;

public interface OrganizationStore {
    Optional<OrganizationContext> findContextForUser(long userId);
    Optional<Long> findOrganizationId();
    /* 读取公司 Owner 用户标识，供需要记录创建者的实例级配置使用。 */
    Optional<Long> findOwnerUserId(long organizationId);
    boolean userExists(String normalizedEmail);
    Optional<Long> findSystemRoleId(String roleCode);
    long createPendingAccount(String email, String normalizedEmail, String displayName, String passwordHash, long roleId);
    long ensureActiveAccount(
            String email, String normalizedEmail, String displayName, String passwordHash, List<Long> roleIds);
    List<OrganizationMember> findMembers(long organizationId);
    Optional<OrganizationMember> findMember(long organizationId, long userId);
    boolean hasActiveMember(long organizationId, long userId);
    void updateMember(
            long organizationId, long userId, String displayName, String status, List<Long> roleIds,
            long expectedVersion);
}
