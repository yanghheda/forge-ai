package ai.forge.server.workitem.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequirementWorkflowRegistryTest {
    private final TransitionGuard allow = context -> GuardResult.allowed();
    private final RequirementWorkflowRegistry registry = new RequirementWorkflowRegistry(
            allow, allow, allow, allow, allow, allow, allow, allow);

    @Test
    void separatesApprovalReviewAndManualUxPreparation() {
        assertTarget(WorkItemStatus.DRAFT, WorkflowAction.SUBMIT_PRODUCT_REVIEW, WorkItemStatus.REQUIREMENT_APPROVAL);
        assertTarget(WorkItemStatus.REQUIREMENT_APPROVAL, WorkflowAction.APPROVE_REQUIREMENT, WorkItemStatus.READY_FOR_REVIEW);
        assertTarget(WorkItemStatus.READY_FOR_REVIEW, WorkflowAction.START_PRODUCT_REVIEW, WorkItemStatus.PRODUCT_REVIEW);
        assertTarget(WorkItemStatus.PRODUCT_REVIEW, WorkflowAction.APPROVE_PRODUCT_REVIEW, WorkItemStatus.READY_FOR_UX);
        assertTarget(WorkItemStatus.READY_FOR_UX, WorkflowAction.START_UX, WorkItemStatus.UX_IN_PROGRESS);
        assertTarget(WorkItemStatus.UX_REVIEW, WorkflowAction.APPROVE_UX_REVIEW, WorkItemStatus.READY_FOR_DEV);
        assertThatThrownBy(() -> registry.require(WorkItemType.REQUIREMENT, WorkItemStatus.PRODUCT_REVIEW, WorkflowAction.SKIP_UX))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void testingFlowsToFixVerificationAndAcceptanceInsteadOfReturningToDevelopment() {
        assertTarget(WorkItemStatus.IN_QA, WorkflowAction.QA_FAIL, WorkItemStatus.IN_FIX_VERIFICATION);
        assertTarget(WorkItemStatus.IN_QA, WorkflowAction.QA_PASS, WorkItemStatus.IN_ACCEPTANCE);
        assertTarget(WorkItemStatus.IN_FIX_VERIFICATION, WorkflowAction.QA_PASS, WorkItemStatus.IN_ACCEPTANCE);
        assertTarget(WorkItemStatus.IN_ACCEPTANCE, WorkflowAction.ACCEPT_REQUIREMENT, WorkItemStatus.READY_FOR_RELEASE);
    }

    @Test
    void allRoleTasksRequireVerificationAndSupportBlocking() {
        for (WorkItemType type : List.of(WorkItemType.UX_TASK, WorkItemType.DEV_TASK, WorkItemType.QA_TASK)) {
            assertThat(type.initialStatus()).isEqualTo(WorkItemStatus.TODO);
            assertThat(registry.require(type, WorkItemStatus.IN_PROGRESS, WorkflowAction.SUBMIT_REVIEW).to())
                    .isEqualTo(WorkItemStatus.WAITING_VERIFICATION);
            assertThat(registry.require(type, WorkItemStatus.WAITING_VERIFICATION, WorkflowAction.APPROVE).to())
                    .isEqualTo(WorkItemStatus.DONE);
            assertThat(registry.require(type, WorkItemStatus.WAITING_VERIFICATION, WorkflowAction.REJECT).to())
                    .isEqualTo(WorkItemStatus.IN_PROGRESS);
            assertThat(registry.require(type, WorkItemStatus.IN_PROGRESS, WorkflowAction.BLOCK).to())
                    .isEqualTo(WorkItemStatus.BLOCKED);
            assertThat(registry.require(type, WorkItemStatus.BLOCKED, WorkflowAction.UNBLOCK).to())
                    .isEqualTo(WorkItemStatus.IN_PROGRESS);
            assertThatThrownBy(() -> registry.require(type, WorkItemStatus.IN_PROGRESS, WorkflowAction.APPROVE))
                    .isInstanceOf(InvalidTransitionException.class);
        }
        assertThat(WorkItemType.BUG.initialStatus()).isEqualTo(WorkItemStatus.TODO);
    }

    @Test
    void taskTypesCannotUseRequirementActionsAndDefinitionsAlwaysRequirePermission() {
        assertThatThrownBy(() -> registry.requireAction(WorkItemType.DEV_TASK, WorkflowAction.APPROVE_PRODUCT_REVIEW))
                .isInstanceOf(InvalidTransitionException.class);
        assertThat(registry.definitions()).allMatch(d -> !d.requiredPermission().isBlank());
    }

    private void assertTarget(WorkItemStatus from, WorkflowAction action, WorkItemStatus to) {
        assertThat(registry.require(WorkItemType.REQUIREMENT, from, action).to()).isEqualTo(to);
    }
}
