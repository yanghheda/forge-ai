package ai.forge.server.workspace.application;

import ai.forge.server.workspace.domain.Workspace;
import ai.forge.server.workspace.domain.WorkspaceMember;
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

    void removeMember(long workspaceId, long userId);
}
