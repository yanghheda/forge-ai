package ai.forge.server.qa.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.qa.domain.QaGuardDecision;
import ai.forge.server.qa.domain.TestCasePriority;
import ai.forge.server.qa.domain.TestResultStatus;
import ai.forge.server.qa.domain.TestRunStatus;
import ai.forge.server.workitem.application.RequirementTransitionService;
import ai.forge.server.workitem.application.WorkItemStore;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import ai.forge.server.workitem.domain.WorkflowAction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class QaServiceTest {

    @Test
    void firstExecutedResultAutomaticallyStartsQa() {
        PermissionEvaluator permissions = mock(PermissionEvaluator.class);
        QaStore qaStore = mock(QaStore.class);
        WorkItemStore workItems = mock(WorkItemStore.class);
        RequirementTransitionService transitions = mock(RequirementTransitionService.class);
        TestResultView pending = result(TestResultStatus.NOT_RUN, 0);
        TestResultView executing = result(TestResultStatus.IN_PROGRESS, 1);
        when(qaStore.findRun(7, 9)).thenReturn(Optional.of(run(pending)));
        when(workItems.findByIdAndScope(7, 5)).thenReturn(Optional.of(requirement(WorkItemStatus.READY_FOR_QA, 4)));
        when(qaStore.updateResult(7, 9, 3, 1, TestResultStatus.IN_PROGRESS, "", List.of(), 0))
                .thenReturn(executing);
        QaService service = new QaService(permissions, qaStore, workItems, transitions);

        TestResultView updated = service.updateResult(
                1, 7, 9, 3, TestResultStatus.IN_PROGRESS, "", List.of(), 0);

        assertThat(updated.status()).isEqualTo(TestResultStatus.IN_PROGRESS);
        InOrder ordered = inOrder(qaStore, transitions);
        ordered.verify(qaStore).updateResult(7, 9, 3, 1, TestResultStatus.IN_PROGRESS, "", List.of(), 0);
        ordered.verify(transitions).transition(1, 7, 5, WorkflowAction.START_QA, 4, "qa-run-9-start", null);
        verify(permissions).requireOrganization(1, 7, "qa.execute");
    }

    @Test
    void laterResultsDoNotRepeatStartQaTransition() {
        PermissionEvaluator permissions = mock(PermissionEvaluator.class);
        QaStore qaStore = mock(QaStore.class);
        WorkItemStore workItems = mock(WorkItemStore.class);
        RequirementTransitionService transitions = mock(RequirementTransitionService.class);
        TestResultView pending = result(TestResultStatus.NOT_RUN, 0);
        when(qaStore.findRun(7, 9)).thenReturn(Optional.of(run(pending)));
        when(workItems.findByIdAndScope(7, 5)).thenReturn(Optional.of(requirement(WorkItemStatus.IN_QA, 5)));
        when(qaStore.updateResult(7, 9, 3, 1, TestResultStatus.PASS, "通过", List.of(), 0))
                .thenReturn(result(TestResultStatus.PASS, 1));
        QaService service = new QaService(permissions, qaStore, workItems, transitions);

        service.updateResult(1, 7, 9, 3, TestResultStatus.PASS, "通过", List.of(), 0);

        verifyNoInteractions(transitions);
    }

    private static TestRunView run(TestResultView result) {
        return new TestRunView(9, 5, "staging", 1, "QA", "USER", TestRunStatus.DRAFT, null,
                QaGuardDecision.noCompletedRun(), List.of(result), null, null, 0);
    }

    private static TestResultView result(TestResultStatus status, long version) {
        return new TestResultView(3, 11, "Login", TestCasePriority.P1, status, "", List.of(), null, null,
                null, version);
    }

    private static WorkItem requirement(WorkItemStatus status, long version) {
        return new WorkItem(5, 7, 1, "REQ-1", WorkItemType.REQUIREMENT, "Login", "", status,
                WorkItemPriority.HIGH, null, 1, null, version, Instant.EPOCH, Instant.EPOCH);
    }
}
