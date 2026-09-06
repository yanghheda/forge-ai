package ai.forge.server.release.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PrecheckRuleRegistryTest {

    private final PrecheckRuleRegistry registry = new PrecheckRuleRegistry();

    @Test
    void allSixRulesPassOnlyFromBackendFacts() {
        PrecheckFacts facts = passingFacts();

        PrecheckDecision decision = registry.evaluate(facts);

        assertThat(decision.passed()).isTrue();
        assertThat(decision.checks()).hasSize(6).allMatch(PrecheckResult::passed);
    }

    @Test
    void reportsEveryNotReadyWorkItem() {
        PrecheckDecision decision = registry.evaluate(passingFacts().withItems(List.of(
                new PrecheckFacts.Item("REL-1", "READY_FOR_RELEASE", 4),
                new PrecheckFacts.Item("REL-2", "IN_QA", 7))));

        assertThat(decision.result(PrecheckRule.WORK_ITEMS_READY).details()).containsExactly("REL-2");
    }

    @Test
    void pipelineMustMatchMergeRequestHead() {
        PrecheckDecision decision = registry.evaluate(passingFacts().withPipelines(List.of(
                new PrecheckFacts.Pipeline("REL-1", "mr://12", "head-sha", "old-sha", "SUCCESS", 8))));

        assertThat(decision.result(PrecheckRule.PIPELINE_GREEN).details()).containsExactly("REL-1:mr://12:head-sha");
    }

    @Test
    void qaRequiresLatestCompletedPassingRun() {
        PrecheckDecision decision = registry.evaluate(passingFacts().withQaRuns(List.of(
                new PrecheckFacts.QaRun("REL-1", 42, "COMPLETED", 3, 1, 0, 9))));

        assertThat(decision.result(PrecheckRule.QA_PASSED).details()).containsExactly("REL-1:run-42:failed=1");
    }

    @Test
    void openAndReopenedCriticalBugsBlockRelease() {
        PrecheckDecision decision = registry.evaluate(passingFacts().withBugs(List.of(
                new PrecheckFacts.Bug("BUG-3", "CRITICAL", "REOPENED", 2),
                new PrecheckFacts.Bug("BUG-4", "BLOCKER", "CLOSED", 5))));

        assertThat(decision.result(PrecheckRule.NO_BLOCKING_BUGS).details()).containsExactly("BUG-3");
    }

    @Test
    void noteAndPolicyFailuresRemainSeparate() {
        PrecheckDecision decision = registry.evaluate(passingFacts().withArtifacts(false).withPolicy(false, 0));

        assertThat(decision.result(PrecheckRule.ARTIFACTS_PRESENT).details()).containsExactly("RELEASE_NOTE");
        assertThat(decision.result(PrecheckRule.APPROVAL_POLICY).details())
                .containsExactly("APPROVER_UNAVAILABLE", "INVALID_TTL");
    }

    private PrecheckFacts passingFacts() {
        return new PrecheckFacts(
                1,
                List.of(new PrecheckFacts.Item("REL-1", "READY_FOR_RELEASE", 4)),
                List.of(new PrecheckFacts.Pipeline("REL-1", "mr://12", "sha", "sha", "SUCCESS", 8)),
                List.of(new PrecheckFacts.QaRun("REL-1", 42, "COMPLETED", 3, 0, 0, 9)),
                List.of(),
                true,
                true,
                60,
                2);
    }
}
