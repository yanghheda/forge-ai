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

    /*
     * 四方评审由产品负责人或该需求产品域执行人发起；
     * 需求审核仍为产品负责人专属，验收则由产品领域成员（负责人或执行人）确认（ADR-024）。
     */
    @Test
    void productExecutorCanStartReviewAndAcceptWhileApprovalStaysLeaderOnly() {
        WorkItem readyForReview = item(WorkItemType.REQUIREMENT, WorkItemStatus.READY_FOR_REVIEW);
        WorkItem inAcceptance = item(WorkItemType.REQUIREMENT, WorkItemStatus.IN_ACCEPTANCE);
        assertThat(policy.canAct(20, readyForReview, WorkflowAction.START_PRODUCT_REVIEW)).isFalse();
        assertThat(policy.canAct(20, inAcceptance, WorkflowAction.ACCEPT_REQUIREMENT)).isFalse();
        when(store.isExecutor(1, 2, "PRODUCT", 20)).thenReturn(true);
        assertThat(policy.canAct(20, readyForReview, WorkflowAction.START_PRODUCT_REVIEW)).isTrue();
        assertThat(policy.canAct(20, inAcceptance, WorkflowAction.ACCEPT_REQUIREMENT)).isTrue();
        assertThat(policy.canAct(20, item(WorkItemType.REQUIREMENT, WorkItemStatus.REQUIREMENT_APPROVAL), WorkflowAction.APPROVE_REQUIREMENT)).isFalse();
        when(store.isLeader(1, 2, "PRODUCT", 10)).thenReturn(true);
        assertThat(policy.canAct(10, readyForReview, WorkflowAction.START_PRODUCT_REVIEW)).isTrue();
        assertThat(policy.canAct(10, inAcceptance, WorkflowAction.ACCEPT_REQUIREMENT)).isTrue();
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

    /*
     * UX 阶段评审：UX 域执行人（设计师）交付完成后可自行提交，
     * 通过与驳回结论只由 UX 负责人给出，避免设计师自评自过。
     */
    @Test
    void uxExecutorCanSubmitStageReviewWhileVerdictStaysLeaderOnly() {
        WorkItem uxInProgress = item(WorkItemType.REQUIREMENT, WorkItemStatus.UX_IN_PROGRESS);
        WorkItem uxReview = item(WorkItemType.REQUIREMENT, WorkItemStatus.UX_REVIEW);
        assertThat(policy.canAct(20, uxInProgress, WorkflowAction.SUBMIT_UX_REVIEW)).isFalse();
        when(store.isExecutor(1, 2, "UX", 20)).thenReturn(true);
        assertThat(policy.canAct(20, uxInProgress, WorkflowAction.SUBMIT_UX_REVIEW)).isTrue();
        assertThat(policy.canAct(20, uxReview, WorkflowAction.APPROVE_UX_REVIEW)).isFalse();
        assertThat(policy.canAct(20, uxReview, WorkflowAction.REJECT_UX_REVIEW)).isFalse();
        when(store.isLeader(1, 2, "UX", 30)).thenReturn(true);
        assertThat(policy.canAct(30, uxReview, WorkflowAction.APPROVE_UX_REVIEW)).isTrue();
        assertThat(policy.canAct(30, uxReview, WorkflowAction.REJECT_UX_REVIEW)).isTrue();
        assertThat(policy.canAct(30, uxInProgress, WorkflowAction.SUBMIT_UX_REVIEW)).isTrue();
    }

    /*
     * 开发与 QA 的阶段提交同样归完成该阶段工作的执行人：
     * 开工（START_DEVELOPMENT / START_QA）仍由领域负责人确认任务后启动。
     */
    @Test
    void developerAndQaExecutorsCanSubmitTheirOwnStageWhileStageStartStaysLeaderOnly() {
        WorkItem inDevelopment = item(WorkItemType.REQUIREMENT, WorkItemStatus.IN_DEVELOPMENT);
        WorkItem inQa = item(WorkItemType.REQUIREMENT, WorkItemStatus.IN_QA);
        assertThat(policy.canAct(20, inDevelopment, WorkflowAction.SUBMIT_FOR_QA)).isFalse();
        assertThat(policy.canAct(20, inQa, WorkflowAction.QA_PASS)).isFalse();
        when(store.isExecutor(1, 2, "DEVELOPER", 20)).thenReturn(true);
        when(store.isExecutor(1, 2, "QA", 20)).thenReturn(true);
        assertThat(policy.canAct(20, inDevelopment, WorkflowAction.SUBMIT_FOR_QA)).isTrue();
        assertThat(policy.canAct(20, inQa, WorkflowAction.QA_PASS)).isTrue();
        assertThat(policy.canAct(20, inQa, WorkflowAction.QA_FAIL)).isTrue();
        assertThat(policy.canAct(20, item(WorkItemType.REQUIREMENT, WorkItemStatus.READY_FOR_DEV), WorkflowAction.START_DEVELOPMENT)).isFalse();
        assertThat(policy.canAct(20, item(WorkItemType.REQUIREMENT, WorkItemStatus.READY_FOR_QA), WorkflowAction.START_QA)).isFalse();
        when(store.isLeader(1, 2, "DEVELOPER", 30)).thenReturn(true);
        when(store.isLeader(1, 2, "QA", 30)).thenReturn(true);
        assertThat(policy.canAct(30, item(WorkItemType.REQUIREMENT, WorkItemStatus.READY_FOR_DEV), WorkflowAction.START_DEVELOPMENT)).isTrue();
        assertThat(policy.canAct(30, item(WorkItemType.REQUIREMENT, WorkItemStatus.READY_FOR_QA), WorkflowAction.START_QA)).isTrue();
    }

    private WorkItem item(WorkItemType type, WorkItemStatus status) {
        return new WorkItem(2, 1, 2, "REQ-2", type, "title", "description", status, WorkItemPriority.MEDIUM,
                20L, 10, null, 0, Instant.EPOCH, Instant.EPOCH);
    }
}
