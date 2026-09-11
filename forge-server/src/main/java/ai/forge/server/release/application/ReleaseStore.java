package ai.forge.server.release.application;

import ai.forge.server.release.domain.PrecheckFacts;
import ai.forge.server.release.domain.PrecheckDecision;
import java.util.List;
import java.util.Optional;

public interface ReleaseStore {

    ReleaseView create(long organizationId, long userId, String versionName, String environment,
            List<Long> itemIds, long approvalTtlMinutes);

    Optional<ReleaseView> find(long organizationId, long releaseId);

    List<ReleaseView> list(long organizationId);

    ReleaseView updateNote(long organizationId, long releaseId, String note, long expectedVersion);

    PrecheckFacts loadFacts(long organizationId, long releaseId);

    PrecheckSnapshot appendPrecheck(long organizationId, long releaseId, long checkedById,
            String checkedByType, PrecheckDecision decision, PrecheckFacts facts);
}
