package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.RequirementDetails;
import java.util.List;
import java.util.Optional;

public interface RequirementDetailsStore {
    Optional<RequirementDetails> find(long organizationId, long workItemId);
    boolean save(long organizationId, long workItemId, String goal, String inScope,
            String outOfScope, List<String> acceptanceCriteria, String businessValue, long expectedVersion);
}
