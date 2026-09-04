package ai.forge.server.project.application;

import ai.forge.server.project.domain.ProjectPolicy;
import java.util.Optional;

public interface ProjectPolicyStore {

    Optional<ProjectPolicy> find(long workspaceId, long projectId);

    boolean update(
            long workspaceId,
            long projectId,
            boolean allowSkipUx,
            long userId,
            long expectedVersion);
}
