package ai.forge.server.qa.domain;

import ai.forge.server.workitem.domain.InvalidTransitionException;
import ai.forge.server.workitem.domain.WorkItemStatus;
import java.util.List;

public final class BugWorkflowRegistry {

    /* Bug 唯一允许的固定迁移集合。 */
    private final List<BugTransition> transitions = List.of(
            transition(WorkItemStatus.TODO, BugAction.START_FIX, WorkItemStatus.IN_PROGRESS, "bug.resolve"),
            transition(WorkItemStatus.IN_PROGRESS, BugAction.RESOLVE, WorkItemStatus.WAITING_VERIFICATION, "bug.resolve"),
            transition(WorkItemStatus.WAITING_VERIFICATION, BugAction.VERIFY, WorkItemStatus.DONE, "bug.verify"),
            transition(WorkItemStatus.WAITING_VERIFICATION, BugAction.REOPEN, WorkItemStatus.IN_PROGRESS, "bug.verify"),
            transition(WorkItemStatus.DONE, BugAction.REOPEN, WorkItemStatus.IN_PROGRESS, "bug.verify"),
            transition(WorkItemStatus.IN_PROGRESS, BugAction.BLOCK, WorkItemStatus.BLOCKED, "bug.resolve"),
            transition(WorkItemStatus.BLOCKED, BugAction.UNBLOCK, WorkItemStatus.IN_PROGRESS, "bug.resolve"));

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
