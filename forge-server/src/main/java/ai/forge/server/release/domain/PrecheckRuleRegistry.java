package ai.forge.server.release.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class PrecheckRuleRegistry {

    /* 会阻断发布的高严重级别集合。 */
    private static final Set<String> BLOCKING_SEVERITIES = Set.of("BLOCKER", "CRITICAL");
    /* 表示缺陷仍未消除的状态集合。 */
    private static final Set<String> BLOCKING_STATUSES = Set.of("OPEN", "REOPENED");

    public PrecheckDecision evaluate(PrecheckFacts facts) {
        List<PrecheckResult> results = List.of(
                workItemsReady(facts), pipelineGreen(facts), qaPassed(facts), noBlockingBugs(facts),
                artifactsPresent(facts), approvalPolicy(facts));
        return new PrecheckDecision(results.stream().allMatch(PrecheckResult::passed), results);
    }

    private PrecheckResult workItemsReady(PrecheckFacts facts) {
        return result(PrecheckRule.WORK_ITEMS_READY, facts.items().stream()
                .filter(item -> !"READY_FOR_RELEASE".equals(item.status()))
                .map(PrecheckFacts.Item::key).toList());
    }

    private PrecheckResult pipelineGreen(PrecheckFacts facts) {
        return result(PrecheckRule.PIPELINE_GREEN, facts.pipelines().stream()
                .filter(pipeline -> !"SUCCESS".equals(pipeline.status())
                        || !pipeline.headSha().equals(pipeline.pipelineSha()))
                .map(pipeline -> pipeline.itemKey() + ":" + pipeline.mergeRequestRef() + ":" + pipeline.headSha())
                .toList());
    }

    private PrecheckResult qaPassed(PrecheckFacts facts) {
        return result(PrecheckRule.QA_PASSED, facts.qaRuns().stream()
                .filter(run -> !"COMPLETED".equals(run.status()) || run.failed() > 0 || run.blocked() > 0)
                .map(run -> run.itemKey() + ":run-" + run.runId() + ":failed=" + run.failed())
                .toList());
    }

    private PrecheckResult noBlockingBugs(PrecheckFacts facts) {
        return result(PrecheckRule.NO_BLOCKING_BUGS, facts.bugs().stream()
                .filter(bug -> BLOCKING_SEVERITIES.contains(bug.severity())
                        && BLOCKING_STATUSES.contains(bug.status()))
                .map(PrecheckFacts.Bug::key).toList());
    }

    private PrecheckResult artifactsPresent(PrecheckFacts facts) {
        return result(PrecheckRule.ARTIFACTS_PRESENT,
                facts.artifactsPresent() ? List.of() : List.of("RELEASE_NOTE"));
    }

    private PrecheckResult approvalPolicy(PrecheckFacts facts) {
        List<String> failures = new ArrayList<>();
        if (!facts.approverAvailable()) {
            failures.add("APPROVER_UNAVAILABLE");
        }
        if (facts.approvalTtlMinutes() <= 0) {
            failures.add("INVALID_TTL");
        }
        return result(PrecheckRule.APPROVAL_POLICY, failures);
    }

    private PrecheckResult result(PrecheckRule rule, List<String> failures) {
        return new PrecheckResult(rule, failures.isEmpty(), failures);
    }
}
