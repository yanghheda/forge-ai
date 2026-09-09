package ai.forge.server.workspace.application;

import ai.forge.server.workspace.domain.Workspace;
import ai.forge.server.workspace.domain.WorkspaceMember;
import ai.forge.server.workspace.domain.OrganizationScope;
import java.util.List;
import java.util.Optional;

public interface WorkspaceStore {

    List<Workspace> findActiveByUserId(long userId);

    Optional<Workspace> findActiveByIdAndUserId(long workspaceId, long userId);

    Optional<Workspace> findActiveBySlugAndUserId(String slug, long userId);

    boolean isActiveOwner(long workspaceId, long userId);

    boolean hasAnyActiveOwnerRole(long userId);

    Workspace createForOwner(long ownerUserId, String name, String slug);

    List<WorkspaceMember> findMembersByWorkspaceId(long workspaceId);

    Optional<Long> findActiveUserIdByNormalizedEmail(String normalizedEmail);

    Optional<Long> findActiveMemberUserIdByNormalizedEmail(long workspaceId, String normalizedEmail);

    void activateMember(long workspaceId, long userId);

    void assignWorkspaceRole(long workspaceId, long userId, long roleId);

    Optional<Long> findSystemRoleIdByCode(String roleCode);

    boolean userExistsByNormalizedEmail(String normalizedEmail);

    Optional<OrganizationScope> findDefaultScopeForUser(long userId);

    Optional<Long> findDefaultWorkspaceId();

    long createSelfRegisteredAccount(
            long workspaceId,
            String email,
            String normalizedEmail,
            String displayName,
            String passwordHash,
            long roleId);

    long createMemberAccount(long workspaceId, String email, String normalizedEmail, String displayName, String passwordHash, long roleId);

    void removeMember(long workspaceId, long userId);
}
