package ai.forge.server.qa.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.qa.domain.TestCasePriority;
import ai.forge.server.qa.domain.TestResultStatus;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class QaService {

    /* 在每个入口执行项目范围最终授权。 */
    private final PermissionEvaluator permissions;

    /* 持有 QA 短事务和显式租户范围持久化。 */
    private final QaStore store;

    public QaService(PermissionEvaluator permissions, QaStore store) {
        this.permissions = permissions;
        this.store = store;
    }

    public TestCaseView createCase(long userId, long organizationId, long requirementId,
            String title, String preconditions, List<String> steps, String expectedResult, TestCasePriority priority) {
        permissions.requireOrganization(userId, organizationId, "qa.manage");
        List<String> normalizedSteps = steps.stream().map(String::trim).filter(step -> !step.isBlank()).toList();
        if (normalizedSteps.isEmpty()) {
            throw new IllegalArgumentException("steps must not be empty");
        }
        return store.createCase(organizationId, requirementId, userId, title.trim(),
                normalize(preconditions), normalizedSteps, expectedResult.trim(), priority);
    }

    public List<TestCaseView> cases(long userId, long organizationId, long requirementId) {
        permissions.requireOrganization(userId, organizationId, "qa.read");
        return store.findCases(organizationId, requirementId);
    }

    public TestRunView createRun(long userId, long organizationId, long requirementId,
            String environment) {
        permissions.requireOrganization(userId, organizationId, "qa.manage");
        return store.createRun(organizationId, requirementId, userId, environment.trim());
    }

    public TestRunView run(long userId, long organizationId, long runId) {
        permissions.requireOrganization(userId, organizationId, "qa.read");
        return store.findRun(organizationId, runId).orElseThrow(ResourceNotFoundException::new);
    }

    public TestRunView latestRun(long userId, long organizationId, long requirementId) {
        permissions.requireOrganization(userId, organizationId, "qa.read");
        return store.findLatestRun(organizationId, requirementId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    public TestResultView updateResult(long userId, long organizationId, long runId, long resultId,
            TestResultStatus status, String actualResult, List<String> evidence, long expectedVersion) {
        permissions.requireOrganization(userId, organizationId, "qa.execute");
        return store.updateResult(organizationId, runId, resultId, userId, status,
                normalize(actualResult), evidence.stream().map(String::trim).filter(value -> !value.isBlank()).toList(),
                expectedVersion);
    }

    public TestRunView completeRun(long userId, long organizationId, long runId,
            long expectedVersion) {
        permissions.requireOrganization(userId, organizationId, "qa.execute");
        return store.completeRun(organizationId, runId, userId, expectedVersion);
    }

    public TestRunView reopenRun(long userId, long organizationId, long runId, long expectedVersion,
            String reason, String requestId) {
        permissions.requireOrganization(userId, organizationId, "qa.execute");
        return store.reopenRun(organizationId, runId, userId, expectedVersion, reason, requestId);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
