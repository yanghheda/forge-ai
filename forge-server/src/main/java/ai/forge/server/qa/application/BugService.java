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

    /* 限制测试建缺陷和已分配开发人员修复的需求范围。 */
    private final ai.forge.server.workitem.application.DeliveryWorkflowPolicy policy;

    public BugService(PermissionEvaluator permissions, WorkItemCommandService workItems, BugStore store, ai.forge.server.workitem.application.DeliveryWorkflowPolicy policy) {
        this.permissions = permissions;
        this.workItems = workItems;
        this.store = store;
        this.policy = policy;
    }

    @Transactional
    public BugView create(long userId, long organizationId, long requirementId, Long testRunId,
            Long testResultId, Long devTaskId, String title, BugSeverity severity,
            List<String> reproductionSteps, String expectedResult, String actualResult) {
        return create(userId, organizationId, requirementId, testRunId, testResultId, devTaskId, title, severity,
                reproductionSteps, expectedResult, actualResult, null);
    }

    @Transactional
    public BugView create(long userId, long organizationId, long requirementId, Long testRunId,
            Long testResultId, Long devTaskId, String title, BugSeverity severity,
            List<String> reproductionSteps, String expectedResult, String actualResult, Long assigneeUserId) {
        permissions.requireOrganization(userId, organizationId, "bug.create");
        List<String> steps = reproductionSteps.stream().map(String::trim).filter(step -> !step.isBlank()).toList();
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("reproductionSteps must not be empty");
        }
        WorkItem item = workItems.createTask(userId, organizationId, requirementId, WorkItemType.BUG, title,
                actualResult, WorkItemPriority.HIGH, assigneeUserId);
        return actions(userId, organizationId, store.create(item, severity, requirementId, testRunId, testResultId, steps,
                expectedResult.trim(), actualResult.trim(), devTaskId));
    }

    public BugView get(long userId, long organizationId, long bugId) {
        permissions.requireOrganization(userId, organizationId, "bug.read");
        return actions(userId, organizationId, store.find(organizationId, bugId).orElseThrow(ResourceNotFoundException::new));
    }

    public List<BugView> list(long userId, long organizationId, Long requirementId) {
        permissions.requireOrganization(userId, organizationId, "bug.read");
        List<BugView> bugs = requirementId == null
                ? store.findAll(organizationId)
                : store.findByRequirement(organizationId, requirementId);
        return bugs.stream().map(bug -> actions(userId, organizationId, bug)).toList();
    }

    @Transactional
    public BugView transition(long userId, long organizationId, long bugId, BugAction action,
            long expectedVersion, String reason, List<String> fixEvidence, String idempotencyKey) {
        policy.lock(organizationId, bugId);
        BugView current = store.find(organizationId, bugId).orElseThrow(ResourceNotFoundException::new);
        if (!policy.testingActive(organizationId, current.requirementId())) throw new ai.forge.server.workitem.domain.InvalidTransitionException();
        boolean verifying = action == BugAction.VERIFY || action == BugAction.REOPEN;
        if (verifying ? !policy.member(organizationId, current.requirementId(), "QA", userId)
                : !Long.valueOf(userId).equals(current.assigneeUserId()) || !policy.member(organizationId, current.requirementId(), "DEVELOPER", userId))
            throw new ResourceNotFoundException();
        BugTransition transition = workflow.require(current.status(), action);
        permissions.requireOrganization(userId, organizationId, transition.requiredPermission());
        String normalizedReason = reason == null ? "" : reason.trim();
        List<String> evidence = fixEvidence == null ? List.of() : fixEvidence.stream()
                .map(String::trim).filter(value -> !value.isBlank()).toList();
        if (action == BugAction.RESOLVE && (normalizedReason.isEmpty() || evidence.isEmpty())) {
            throw new IllegalArgumentException("RESOLVE requires fix note and MR or commit evidence");
        }
        if ((action == BugAction.REOPEN || action == BugAction.BLOCK || action == BugAction.UNBLOCK) && normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("REOPEN requires a reason");
        }
        return actions(userId, organizationId, store.transition(organizationId, bugId, userId, action,
                transition.to().name(), normalizedReason, evidence, expectedVersion, idempotencyKey));
    }
    private BugView actions(long userId, long organizationId, BugView bug) {
        if (!policy.testingActive(organizationId, bug.requirementId())) return bug.withActions(List.of());
        List<BugAction> allowed = new java.util.ArrayList<>();
        for (BugAction action : BugAction.values()) {
            try {
                BugTransition transition = workflow.require(bug.status(), action);
                boolean verifying = action == BugAction.VERIFY || action == BugAction.REOPEN;
                boolean assigned = verifying ? policy.member(organizationId, bug.requirementId(), "QA", userId)
                        : Long.valueOf(userId).equals(bug.assigneeUserId()) && policy.member(organizationId, bug.requirementId(), "DEVELOPER", userId);
                if (assigned && permissions.hasOrganizationPermission(userId, organizationId, transition.requiredPermission())) allowed.add(action);
            } catch (ai.forge.server.workitem.domain.InvalidTransitionException ignored) {
                // 不在当前状态注册的动作不向调用方展示。
            }
        }
        return bug.withActions(List.copyOf(allowed));
    }
}
