package ai.forge.server.project.application;

import ai.forge.server.project.domain.Project;
import ai.forge.server.project.domain.ProjectMember;
import java.util.List;
import java.util.Optional;

public interface ProjectStore {

    Project createWithCreatorMembership(long workspaceId, long creatorUserId, String key, String name, String description);

    List<Project> findByWorkspaceId(long workspaceId);

    Optional<Project> findByIdAndWorkspaceId(long projectId, long workspaceId);

    boolean hasActiveMember(long workspaceId, long projectId, long userId);

    boolean archive(long workspaceId, long projectId, long expectedVersion);

    List<ProjectMember> findMembersByProjectIdAndWorkspaceId(long projectId, long workspaceId);

    void activateMember(long workspaceId, long projectId, long userId);

    void removeMember(long workspaceId, long projectId, long userId);
}
