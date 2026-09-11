package ai.forge.server.qa.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.qa.domain.BugAction;
import ai.forge.server.qa.domain.BugSeverity;
import ai.forge.server.qa.domain.BugTransition;
import ai.forge.server.qa.domain.BugWorkflowRegistry;
import ai.forge.server.workitem.application.WorkItemCommandService;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemType;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test-unit")
public class BugService {

    /* 在真实项目范围执行 Bug 动作权限检查。 */
    private final PermissionEvaluator permissions;

    /* 复用 Work Item 原子编号与创建规则。 */
    private final WorkItemCommandService workItems;

    /* 原子持久化 Bug 详情、关系、状态和事件。 */
    private final BugStore store;

    /* Bug 生命周期的唯一固定定义。 */
    private final BugWorkflowRegistry workflow = new BugWorkflowRegistry();

    public BugService(PermissionEvaluator permissions, WorkItemCommandService workItems, BugStore store) {
        this.permissions = permissions;
        this.workItems = workItems;
        this.store = store;
    }

    @Transactional
    public BugView create(long userId, long organizationId, long requirementId, Long testRunId,
            Long testResultId, Long devTaskId, String title, BugSeverity severity,
            List<String> reproductionSteps, String expectedResult, String actualResult) {
        permissions.requireOrganization(userId, organizationId, "bug.create");
        List<String> steps = reproductionSteps.stream().map(String::trim).filter(step -> !step.isBlank()).toList();
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("reproductionSteps must not be empty");
        }
        WorkItem item = workItems.create(userId, organizationId, WorkItemType.BUG, title,
                actualResult, WorkItemPriority.HIGH, null, null);
        return store.create(item, severity, requirementId, testRunId, testResultId, steps,
                expectedResult.trim(), actualResult.trim(), devTaskId);
    }

    public BugView get(long userId, long organizationId, long bugId) {
        permissions.requireOrganization(userId, organizationId, "bug.read");
        return store.find(organizationId, bugId).orElseThrow(ResourceNotFoundException::new);
    }

    public List<BugView> list(long userId, long organizationId, long requirementId) {
        permissions.requireOrganization(userId, organizationId, "bug.read");
        return store.findByRequirement(organizationId, requirementId);
    }

    @Transactional
    public BugView transition(long userId, long organizationId, long bugId, BugAction action,
            long expectedVersion, String reason, List<String> fixEvidence, String idempotencyKey) {
        BugView current = store.find(organizationId, bugId).orElseThrow(ResourceNotFoundException::new);
        BugTransition transition = workflow.require(current.status(), action);
        permissions.requireOrganization(userId, organizationId, transition.requiredPermission());
        String normalizedReason = reason == null ? "" : reason.trim();
        List<String> evidence = fixEvidence == null ? List.of() : fixEvidence.stream()
                .map(String::trim).filter(value -> !value.isBlank()).toList();
        if (action == BugAction.RESOLVE && (normalizedReason.isEmpty() || evidence.isEmpty())) {
            throw new IllegalArgumentException("RESOLVE requires fix note and MR or commit evidence");
        }
        if (action == BugAction.REOPEN && normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("REOPEN requires a reason");
        }
        return store.transition(organizationId, bugId, userId, action,
                transition.to().name(), normalizedReason, evidence, expectedVersion, idempotencyKey);
    }
}
