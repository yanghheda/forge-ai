package ai.forge.server.qa.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.qa.domain.TestCasePriority;
import ai.forge.server.qa.domain.TestCaseType;
import ai.forge.server.qa.domain.TestResultStatus;
import ai.forge.server.workitem.application.RequirementTransitionService;
import ai.forge.server.workitem.application.WorkItemStore;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkflowAction;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test-unit")
public class QaService {

    /* 在每个入口执行项目范围最终授权。 */
    private final PermissionEvaluator permissions;

    /* 持有 QA 短事务和显式租户范围持久化。 */
    private final QaStore store;

    /* 读取 Requirement 当前状态和乐观锁版本。 */
    private final WorkItemStore workItems;

    /* 通过统一状态机推进 Requirement，保留权限与审计语义。 */
    private final RequirementTransitionService transitions;

    /* 限定需求测试成员并锁定阶段，避免执行结果与需求推进竞态。 */
    private final ai.forge.server.workitem.application.DeliveryWorkflowPolicy policy;

    public QaService(PermissionEvaluator permissions, QaStore store, WorkItemStore workItems,
            RequirementTransitionService transitions, ai.forge.server.workitem.application.DeliveryWorkflowPolicy policy) {
        this.permissions = permissions;
        this.store = store;
        this.workItems = workItems;
        this.transitions = transitions;
        this.policy = policy;
    }

    @Transactional
    public TestCaseView createCase(long userId, long organizationId, long requirementId,
            String title, String preconditions, List<String> steps, String expectedResult, TestCasePriority priority,
            TestCaseType caseType) {
        permissions.requireOrganization(userId, organizationId, "qa.manage");
        requireMember(userId, organizationId, requirementId);
        List<String> normalizedSteps = steps.stream().map(String::trim).filter(step -> !step.isBlank()).toList();
        if (normalizedSteps.isEmpty()) {
            throw new IllegalArgumentException("steps must not be empty");
        }
        return store.createCase(organizationId, requirementId, userId, title.trim(),
                normalize(preconditions), normalizedSteps, expectedResult.trim(), priority, caseType);
    }

    public List<TestCaseView> cases(long userId, long organizationId, long requirementId) {
        permissions.requireOrganization(userId, organizationId, "qa.read");
        return store.findCases(organizationId, requirementId);
    }

    @Transactional
    public TestRunView createRun(long userId, long organizationId, long requirementId,
            String environment) {
        permissions.requireOrganization(userId, organizationId, "qa.manage");
        requireTestingStage(userId, organizationId, requirementId, true);
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

    @Transactional
    public TestResultView updateResult(long userId, long organizationId, long runId, long resultId,
            TestResultStatus status, String actualResult, List<String> evidence, long expectedVersion) {
        permissions.requireOrganization(userId, organizationId, "qa.execute");
        TestRunView run = store.findRun(organizationId, runId).orElseThrow(ResourceNotFoundException::new);
        requireTestingStage(userId, organizationId, run.requirementId(), false);
        TestResultView updated = store.updateResult(organizationId, runId, resultId, userId, status,
                normalize(actualResult), evidence.stream().map(String::trim).filter(value -> !value.isBlank()).toList(),
                expectedVersion);
        return updated;
    }

    @Transactional
    public TestRunView completeRun(long userId, long organizationId, long runId,
            long expectedVersion) {
        permissions.requireOrganization(userId, organizationId, "qa.execute");
        TestRunView run = store.findRun(organizationId, runId).orElseThrow(ResourceNotFoundException::new);
        requireTestingStage(userId, organizationId, run.requirementId(), false);
        return store.completeRun(organizationId, runId, userId, expectedVersion);
    }

    @Transactional
    public TestRunView reopenRun(long userId, long organizationId, long runId, long expectedVersion,
            String reason, String requestId) {
        permissions.requireOrganization(userId, organizationId, "qa.execute");
        TestRunView run = store.findRun(organizationId, runId).orElseThrow(ResourceNotFoundException::new);
        requireTestingStage(userId, organizationId, run.requirementId(), false);
        return store.reopenRun(organizationId, runId, userId, expectedVersion, reason, requestId);
    }

    private void requireMember(long userId, long organizationId, long requirementId) {
        policy.lock(organizationId, requirementId);
        if (!policy.member(organizationId, requirementId, "QA", userId)) throw new ResourceNotFoundException();
    }

    private void requireTestingStage(long userId, long organizationId, long requirementId, boolean preparationAllowed) {
        requireMember(userId, organizationId, requirementId);
        var item = workItems.findByIdAndScope(organizationId, requirementId).orElseThrow(ResourceNotFoundException::new);
        if (item.status() != WorkItemStatus.IN_QA && item.status() != WorkItemStatus.IN_FIX_VERIFICATION
                && !(preparationAllowed && item.status() == WorkItemStatus.READY_FOR_QA)) throw new QaStateException();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
