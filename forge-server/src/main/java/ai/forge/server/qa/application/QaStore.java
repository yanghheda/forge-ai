package ai.forge.server.qa.application;

import ai.forge.server.qa.domain.TestCasePriority;
import ai.forge.server.qa.domain.TestResultStatus;
import ai.forge.server.qa.domain.TestRunSummary;
import java.util.List;
import java.util.Optional;

public interface QaStore {

    TestCaseView createCase(long workspaceId, long projectId, long requirementId, long userId,
            String title, String preconditions, List<String> steps, String expectedResult, TestCasePriority priority);

    List<TestCaseView> findCases(long workspaceId, long projectId, long requirementId);

    TestRunView createRun(long workspaceId, long projectId, long requirementId, long userId, String environment);

    Optional<TestRunView> findRun(long workspaceId, long projectId, long runId);

    Optional<TestRunView> findLatestRun(long workspaceId, long projectId, long requirementId);

    TestResultView updateResult(long workspaceId, long projectId, long runId, long resultId, long userId,
            TestResultStatus status, String actualResult, List<String> evidence, long expectedVersion);

    TestRunView completeRun(long workspaceId, long projectId, long runId, long userId, long expectedVersion);

    TestRunView reopenRun(long workspaceId, long projectId, long runId, long userId, long expectedVersion,
            String reason, String requestId);

    Optional<TestRunSummary> findLatestCompletedSummary(long workspaceId, long projectId, long requirementId);
}
