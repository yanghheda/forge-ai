package ai.forge.server.workitem.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.workitem.domain.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveryWorkflowPolicyTest {
    private final DeliveryWorkflowStore store = mock(DeliveryWorkflowStore.class);
    private final OrganizationRequirementStore participants = mock(OrganizationRequirementStore.class);
    private final DeliveryWorkflowPolicy policy = new DeliveryWorkflowPolicy(store, participants);

    @Test
    void companyRoleAloneCannotApproveOrCreateAnotherRequirementsTasks() {
        assertThat(policy.canAct(10, item(WorkItemType.REQUIREMENT, WorkItemStatus.UX_REVIEW), WorkflowAction.APPROVE_UX_REVIEW)).isFalse();
        assertThatThrownBy(() -> policy.requireCreation(10, item(WorkItemType.REQUIREMENT, WorkItemStatus.READY_FOR_UX), WorkItemType.UX_TASK, 20L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void leaderCanOnlyCreateForAssignedExecutorInPreparationStage() {
        when(store.isLeader(1, 2, "UX", 10)).thenReturn(true);
        when(store.isExecutor(1, 2, "UX", 20)).thenReturn(true);
        assertThatCode(() -> policy.requireCreation(10, item(WorkItemType.REQUIREMENT, WorkItemStatus.READY_FOR_UX), WorkItemType.UX_TASK, 20L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireCreation(10, item(WorkItemType.REQUIREMENT, WorkItemStatus.UX_IN_PROGRESS), WorkItemType.UX_TASK, 20L))
                .isInstanceOf(InvalidTransitionException.class);
        assertThatThrownBy(() -> policy.requireCreation(10, item(WorkItemType.REQUIREMENT, WorkItemStatus.READY_FOR_UX), WorkItemType.UX_TASK, 30L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void testingCannotFinishBeforeEveryCaseWasExecutedAndBugsBlockAcceptance() {
        assertThat(policy.missing(item(WorkItemType.REQUIREMENT, WorkItemStatus.IN_QA), WorkflowAction.QA_FAIL)).contains("allTestCasesExecuted");
        when(store.allCasesExecuted(1, 2)).thenReturn(true);
        assertThat(policy.missing(item(WorkItemType.REQUIREMENT, WorkItemStatus.IN_QA), WorkflowAction.QA_FAIL)).isEmpty();
        when(store.incompleteBugs(1, 2)).thenReturn(1);
        assertThat(policy.missing(item(WorkItemType.REQUIREMENT, WorkItemStatus.IN_QA), WorkflowAction.QA_PASS)).contains("unfinishedBugs");
    }

    @Test
    void executorCanSubmitButOnlyDomainLeaderCanApprove() {
        when(store.requirementId(1, 2)).thenReturn(3L);
        when(store.isExecutor(1, 3, "UX", 20)).thenReturn(true);
        assertThat(policy.canAct(20, item(WorkItemType.UX_TASK, WorkItemStatus.IN_PROGRESS), WorkflowAction.SUBMIT_REVIEW)).isTrue();
        assertThat(policy.canAct(20, item(WorkItemType.UX_TASK, WorkItemStatus.WAITING_VERIFICATION), WorkflowAction.APPROVE)).isFalse();
        when(store.isLeader(1, 3, "UX", 10)).thenReturn(true);
        assertThat(policy.canAct(10, item(WorkItemType.UX_TASK, WorkItemStatus.WAITING_VERIFICATION), WorkflowAction.APPROVE)).isTrue();
    }

    private WorkItem item(WorkItemType type, WorkItemStatus status) {
        return new WorkItem(2, 1, 2, "REQ-2", type, "title", "description", status, WorkItemPriority.MEDIUM,
                20L, 10, null, 0, Instant.EPOCH, Instant.EPOCH);
    }
}
