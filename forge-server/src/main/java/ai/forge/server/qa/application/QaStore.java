package ai.forge.server.qa.application;

import ai.forge.server.qa.domain.TestCasePriority;
import ai.forge.server.qa.domain.TestResultStatus;
import ai.forge.server.qa.domain.TestRunSummary;
import java.util.List;
import java.util.Optional;

public interface QaStore {

    TestCaseView createCase(long organizationId, long requirementId, long userId,
            String title, String preconditions, List<String> steps, String expectedResult, TestCasePriority priority);

    List<TestCaseView> findCases(long organizationId, long requirementId);

    TestRunView createRun(long organizationId, long requirementId, long userId, String environment);

    Optional<TestRunView> findRun(long organizationId, long runId);

    Optional<TestRunView> findLatestRun(long organizationId, long requirementId);

    TestResultView updateResult(long organizationId, long runId, long resultId, long userId,
            TestResultStatus status, String actualResult, List<String> evidence, long expectedVersion);

    TestRunView completeRun(long organizationId, long runId, long userId, long expectedVersion);

    TestRunView reopenRun(long organizationId, long runId, long userId, long expectedVersion,
            String reason, String requestId);

    Optional<TestRunSummary> findLatestCompletedSummary(long organizationId, long requirementId);
}
