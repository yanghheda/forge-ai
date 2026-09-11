package ai.forge.server.workitem.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.workitem.domain.TransitionContext;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DevelopmentQaGuardTest {

    @Test
    void reportsMissingAndIncompleteDevTasksBeforeCiChecks() {
        DevelopmentQaGuard missingGuard = new DevelopmentQaGuard(
                (organizationId, requirementId) -> DevelopmentQaSummary.empty(requirementId, true));
        DevelopmentQaGuard incompleteGuard = new DevelopmentQaGuard(
                (organizationId, requirementId) -> new DevelopmentQaSummary(
                        requirementId,
                        true,
                        true,
                        List.of(task("FORGE-2", WorkItemStatus.IN_PROGRESS, true, "head", "head", "success"))));

        assertThat(missingGuard.evaluate(context()).missing()).containsExactly("devTask");
        assertThat(incompleteGuard.evaluate(context()).missing()).containsExactly("devTaskIncomplete");
    }

    @Test
    void explainsEachRequiredCiFailureAndRejectsAStaleSuccessfulPipeline() {
        assertThat(evaluate(new DevelopmentQaSummary(1L, true, false, List.of(doneTask(false, null, null, null)))))
                .containsExactly("repository");
        assertThat(evaluate(summary(doneTask(false, null, null, null))))
                .containsExactly("mergeRequest");
        assertThat(evaluate(summary(doneTask(true, "head", null, null))))
                .containsExactly("pipeline");
        assertThat(evaluate(summary(doneTask(true, "head", "head", "running"))))
                .containsExactly("pipelineRunning");
        assertThat(evaluate(summary(doneTask(true, "head", "head", "failed"))))
                .containsExactly("pipelineFailed");
        assertThat(evaluate(summary(doneTask(true, "head-new", "head-old", "success"))))
                .containsExactly("pipelineHeadMismatch");
    }

    @Test
    void ciOptionalOnlyRequiresCompletedTasksAndCurrentHeadSuccessPassesRequiredCi() {
        DevelopmentQaSummary optional = new DevelopmentQaSummary(
                1L, false, false, List.of(doneTask(false, null, null, null)));
        DevelopmentQaSummary required = summary(doneTask(true, "head", "head", "success"));

        assertThat(evaluate(optional)).isEmpty();
        assertThat(evaluate(required)).isEmpty();
    }

    private static List<String> evaluate(DevelopmentQaSummary summary) {
        return new DevelopmentQaGuard((organizationId, requirementId) -> summary)
                .evaluate(context())
                .missing();
    }

    private static DevelopmentQaSummary summary(DevelopmentQaTask task) {
        return new DevelopmentQaSummary(1L, true, true, List.of(task));
    }

    private static DevelopmentQaTask doneTask(
            boolean hasMergeRequest,
            String mergeRequestHeadSha,
            String pipelineCommitSha,
            String pipelineStatus) {
        return task(
                "FORGE-2",
                WorkItemStatus.DONE,
                hasMergeRequest,
                mergeRequestHeadSha,
                pipelineCommitSha,
                pipelineStatus);
    }

    private static DevelopmentQaTask task(
            String itemKey,
            WorkItemStatus status,
            boolean hasMergeRequest,
            String mergeRequestHeadSha,
            String pipelineCommitSha,
            String pipelineStatus) {
        return new DevelopmentQaTask(
                2L,
                itemKey,
                "Implement",
                status,
                0L,
                null,
                null,
                hasMergeRequest ? 7L : null,
                hasMergeRequest ? "https://gitlab.example/mr/7" : null,
                mergeRequestHeadSha,
                pipelineCommitSha == null ? null : 9L,
                pipelineCommitSha,
                pipelineStatus,
                null);
    }

    private static TransitionContext context() {
        WorkItem requirement = new WorkItem(
                1L,
                3L,
                1L,
                "FORGE-1",
                WorkItemType.REQUIREMENT,
                "Requirement",
                "",
                WorkItemStatus.IN_DEVELOPMENT,
                WorkItemPriority.HIGH,
                null,
                5L,
                null,
                0L,
                Instant.EPOCH,
                Instant.EPOCH);
        return new TransitionContext(requirement, null, null);
    }
}
