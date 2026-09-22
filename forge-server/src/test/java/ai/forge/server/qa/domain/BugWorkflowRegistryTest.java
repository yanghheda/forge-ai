package ai.forge.server.qa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.forge.server.workitem.domain.InvalidTransitionException;
import ai.forge.server.workitem.domain.WorkItemStatus;
import org.junit.jupiter.api.Test;

class BugWorkflowRegistryTest {

    private final BugWorkflowRegistry registry = new BugWorkflowRegistry();

    @Test
    void followsFixVerifyCloseLifecycleWithRoleSpecificPermissions() {
        assertTransition(WorkItemStatus.TODO, BugAction.START_FIX, WorkItemStatus.IN_PROGRESS, "bug.resolve");
        assertTransition(WorkItemStatus.IN_PROGRESS, BugAction.RESOLVE, WorkItemStatus.WAITING_VERIFICATION, "bug.resolve");
        assertTransition(WorkItemStatus.WAITING_VERIFICATION, BugAction.VERIFY, WorkItemStatus.DONE, "bug.verify");
        assertTransition(WorkItemStatus.IN_PROGRESS, BugAction.BLOCK, WorkItemStatus.BLOCKED, "bug.resolve");
        assertTransition(WorkItemStatus.BLOCKED, BugAction.UNBLOCK, WorkItemStatus.IN_PROGRESS, "bug.resolve");
    }

    @Test
    void reopensResolvedOrVerifiedBugButCannotSkipVerification() {
        assertTransition(WorkItemStatus.WAITING_VERIFICATION, BugAction.REOPEN, WorkItemStatus.IN_PROGRESS, "bug.verify");
        assertTransition(WorkItemStatus.DONE, BugAction.REOPEN, WorkItemStatus.IN_PROGRESS, "bug.verify");
        assertThatThrownBy(() -> registry.require(WorkItemStatus.WAITING_VERIFICATION, BugAction.CLOSE))
                .isInstanceOf(InvalidTransitionException.class);
    }

    private void assertTransition(WorkItemStatus from, BugAction action, WorkItemStatus to, String permission) {
        BugTransition transition = registry.require(from, action);
        assertThat(transition.to()).isEqualTo(to);
        assertThat(transition.requiredPermission()).isEqualTo(permission);
    }
}
