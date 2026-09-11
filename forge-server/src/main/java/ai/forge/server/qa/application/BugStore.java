package ai.forge.server.qa.application;

import ai.forge.server.qa.domain.BugAction;
import ai.forge.server.qa.domain.BugSeverity;
import ai.forge.server.workitem.domain.WorkItem;
import java.util.List;
import java.util.Optional;

public interface BugStore {
    BugView create(WorkItem item, BugSeverity severity, long requirementId, Long testRunId, Long testResultId,
            List<String> reproductionSteps, String expectedResult, String actualResult, Long devTaskId);

    Optional<BugView> find(long organizationId, long bugId);

    BugView transition(long organizationId, long bugId, long userId, BugAction action,
            String toStatus, String reason, List<String> fixEvidence, long expectedVersion, String idempotencyKey);

    List<BugView> findByRequirement(long organizationId, long requirementId);
}
