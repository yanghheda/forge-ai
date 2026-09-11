package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.GuardResult;
import ai.forge.server.workitem.domain.TransitionContext;
import ai.forge.server.workitem.domain.TransitionGuard;
import ai.forge.server.workitem.domain.WorkItemStatus;
import java.util.LinkedHashSet;
import java.util.Locale;

public final class DevelopmentQaGuard implements TransitionGuard {

    /* 读取项目策略与 GitLab 本地同步快照，不触发远端调用。 */
    private final DevelopmentQaStore store;

    public DevelopmentQaGuard(DevelopmentQaStore store) {
        this.store = store;
    }

    @Override
    public GuardResult evaluate(TransitionContext context) {
        DevelopmentQaSummary summary = store.summarize(
                context.workItem().organizationId(),
                context.workItem().id());
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        if (summary.tasks().isEmpty()) {
            missing.add("devTask");
            return new GuardResult(missing.stream().toList());
        }
        if (summary.tasks().stream().anyMatch(task -> task.status() != WorkItemStatus.DONE)) {
            missing.add("devTaskIncomplete");
        }
        if (!summary.ciRequired()) {
            return new GuardResult(missing.stream().toList());
        }
        if (!summary.repositoryConfigured()) {
            missing.add("repository");
            return new GuardResult(missing.stream().toList());
        }
        for (DevelopmentQaTask task : summary.tasks()) {
            evaluateCi(task, missing);
        }
        return new GuardResult(missing.stream().toList());
    }

    private void evaluateCi(DevelopmentQaTask task, LinkedHashSet<String> missing) {
        if (task.mergeRequestId() == null) {
            missing.add("mergeRequest");
            return;
        }
        if (task.pipelineId() == null) {
            missing.add("pipeline");
            return;
        }
        if (task.mergeRequestHeadSha() == null
                || !task.mergeRequestHeadSha().equals(task.pipelineCommitSha())) {
            missing.add("pipelineHeadMismatch");
            return;
        }
        String status = task.pipelineStatus() == null
                ? ""
                : task.pipelineStatus().toLowerCase(Locale.ROOT);
        if (status.equals("running") || status.equals("pending") || status.equals("created")) {
            missing.add("pipelineRunning");
        } else if (!status.equals("success")) {
            missing.add("pipelineFailed");
        }
    }
}
