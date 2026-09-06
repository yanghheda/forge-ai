package ai.forge.server.qa.domain;

import ai.forge.server.workitem.domain.InvalidTransitionException;
import ai.forge.server.workitem.domain.WorkItemStatus;
import java.util.List;

public final class BugWorkflowRegistry {

    /* Bug 唯一允许的固定迁移集合。 */
    private final List<BugTransition> transitions = List.of(
            transition(WorkItemStatus.OPEN, BugAction.START_FIX, WorkItemStatus.IN_PROGRESS, "bug.resolve"),
            transition(WorkItemStatus.IN_PROGRESS, BugAction.RESOLVE, WorkItemStatus.RESOLVED, "bug.resolve"),
            transition(WorkItemStatus.RESOLVED, BugAction.VERIFY, WorkItemStatus.VERIFIED, "bug.verify"),
            transition(WorkItemStatus.VERIFIED, BugAction.CLOSE, WorkItemStatus.CLOSED, "bug.verify"),
            transition(WorkItemStatus.RESOLVED, BugAction.REOPEN, WorkItemStatus.OPEN, "bug.verify"),
            transition(WorkItemStatus.VERIFIED, BugAction.REOPEN, WorkItemStatus.OPEN, "bug.verify"),
            transition(WorkItemStatus.CLOSED, BugAction.REOPEN, WorkItemStatus.OPEN, "bug.verify"),
            transition(WorkItemStatus.OPEN, BugAction.CANCEL, WorkItemStatus.CANCELLED, "bug.edit"),
            transition(WorkItemStatus.IN_PROGRESS, BugAction.CANCEL, WorkItemStatus.CANCELLED, "bug.edit"));

    public BugTransition require(WorkItemStatus from, BugAction action) {
        return transitions.stream()
                .filter(candidate -> candidate.from() == from && candidate.action() == action)
                .findFirst()
                .orElseThrow(InvalidTransitionException::new);
    }

    private static BugTransition transition(
            WorkItemStatus from, BugAction action, WorkItemStatus to, String permission) {
        return new BugTransition(from, action, to, permission);
    }
}
