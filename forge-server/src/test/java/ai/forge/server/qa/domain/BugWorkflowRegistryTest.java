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
        assertTransition(WorkItemStatus.OPEN, BugAction.START_FIX, WorkItemStatus.IN_PROGRESS, "bug.resolve");
        assertTransition(WorkItemStatus.IN_PROGRESS, BugAction.RESOLVE, WorkItemStatus.RESOLVED, "bug.resolve");
        assertTransition(WorkItemStatus.RESOLVED, BugAction.VERIFY, WorkItemStatus.VERIFIED, "bug.verify");
        assertTransition(WorkItemStatus.VERIFIED, BugAction.CLOSE, WorkItemStatus.CLOSED, "bug.verify");
    }

    @Test
    void reopensResolvedOrVerifiedBugButCannotSkipVerification() {
        assertTransition(WorkItemStatus.RESOLVED, BugAction.REOPEN, WorkItemStatus.OPEN, "bug.verify");
        assertTransition(WorkItemStatus.VERIFIED, BugAction.REOPEN, WorkItemStatus.OPEN, "bug.verify");
        assertThatThrownBy(() -> registry.require(WorkItemStatus.RESOLVED, BugAction.CLOSE))
                .isInstanceOf(InvalidTransitionException.class);
    }

    private void assertTransition(WorkItemStatus from, BugAction action, WorkItemStatus to, String permission) {
        BugTransition transition = registry.require(from, action);
        assertThat(transition.to()).isEqualTo(to);
        assertThat(transition.requiredPermission()).isEqualTo(permission);
    }
}
