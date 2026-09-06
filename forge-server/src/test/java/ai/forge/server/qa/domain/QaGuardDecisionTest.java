package ai.forge.server.qa.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QaGuardDecisionTest {

    @Test
    void passesOnlyWhenMandatoryCasesPassAndOnlyOptionalCasesAreSkipped() {
        assertThat(QaGuardDecision.from(summary(2, 0, 0, 1, 0)).passed()).isTrue();
        assertThat(QaGuardDecision.from(summary(1, 1, 0, 0, 0)).missing())
                .containsExactly("mandatoryTestSkipped");
        assertThat(QaGuardDecision.from(summary(1, 0, 1, 0, 0)).missing())
                .containsExactly("testFailed");
        assertThat(QaGuardDecision.from(summary(1, 0, 0, 0, 1)).missing())
                .containsExactly("testBlocked");
    }

    @Test
    void rejectsMissingOrIncompleteRuns() {
        assertThat(QaGuardDecision.noCompletedRun().missing()).containsExactly("completedTestRun");
        assertThat(QaGuardDecision.from(new TestRunSummary(1, 0, 0, 0, 0, 1, 0)).missing())
                .containsExactly("testNotRun");
    }

    private static TestRunSummary summary(
            int passed,
            int mandatorySkipped,
            int failed,
            int optionalSkipped,
            int blocked) {
        int total = passed + mandatorySkipped + failed + optionalSkipped + blocked;
        return new TestRunSummary(total, passed, failed, blocked, optionalSkipped + mandatorySkipped, 0,
                mandatorySkipped);
    }
}
