package ai.forge.server.release.application;

import ai.forge.server.release.domain.PrecheckFacts;
import ai.forge.server.release.domain.PrecheckDecision;
import java.util.List;
import java.util.Optional;

public interface ReleaseStore {

    ReleaseView create(long workspaceId, long projectId, long userId, String versionName, String environment,
            List<Long> itemIds, long approvalTtlMinutes);

    Optional<ReleaseView> find(long workspaceId, long projectId, long releaseId);

    List<ReleaseView> list(long workspaceId, long projectId);

    ReleaseView updateNote(long workspaceId, long projectId, long releaseId, String note, long expectedVersion);

    PrecheckFacts loadFacts(long workspaceId, long projectId, long releaseId);

    PrecheckSnapshot appendPrecheck(long workspaceId, long projectId, long releaseId, long checkedById,
            String checkedByType, PrecheckDecision decision, PrecheckFacts facts);
}
