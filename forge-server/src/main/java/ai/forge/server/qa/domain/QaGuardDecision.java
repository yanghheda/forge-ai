package ai.forge.server.qa.domain;

import java.util.ArrayList;
import java.util.List;

public record QaGuardDecision(
        /* 是否满足进入发布准备状态的确定性条件。 */ boolean passed,
        /* 未满足的机器可读规则集合。 */ List<String> missing) {

    public static QaGuardDecision noCompletedRun() {
        return new QaGuardDecision(false, List.of("completedTestRun"));
    }

    public static QaGuardDecision from(TestRunSummary summary) {
        List<String> missing = new ArrayList<>();
        if (summary.notRun() > 0 || summary.total() == 0) {
            missing.add("testNotRun");
        }
        if (summary.failed() > 0) {
            missing.add("testFailed");
        }
        if (summary.blocked() > 0) {
            missing.add("testBlocked");
        }
        if (summary.mandatorySkipped() > 0) {
            missing.add("mandatoryTestSkipped");
        }
        return new QaGuardDecision(missing.isEmpty(), List.copyOf(missing));
    }
}
