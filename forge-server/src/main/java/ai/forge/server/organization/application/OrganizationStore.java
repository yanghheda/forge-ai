package ai.forge.server.organization.application;

import ai.forge.server.organization.domain.OrganizationContext;
import ai.forge.server.organization.domain.OrganizationMember;
import java.util.List;
import java.util.Optional;

public interface OrganizationStore {
    Optional<OrganizationContext> findContextForUser(long userId);
    Optional<Long> findOrganizationId();
    boolean userExists(String normalizedEmail);
    Optional<Long> findSystemRoleId(String roleCode);
    long createPendingAccount(String email, String normalizedEmail, String displayName, String passwordHash, long roleId);
    List<OrganizationMember> findMembers(long organizationId);
    Optional<OrganizationMember> findMember(long organizationId, long userId);
    boolean hasActiveMember(long organizationId, long userId);
    void updateMember(long organizationId, long userId, String status, long roleId, long expectedVersion);
}
