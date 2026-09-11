package ai.forge.server.qa.application;

import ai.forge.server.qa.domain.QaGuardDecision;
import ai.forge.server.workitem.domain.GuardResult;
import ai.forge.server.workitem.domain.TransitionContext;
import ai.forge.server.workitem.domain.TransitionGuard;

public final class QaPassGuard implements TransitionGuard {

    /* 查询最新已完成 Run 的不可变统计。 */
    private final QaStore store;

    public QaPassGuard(QaStore store) {
        this.store = store;
    }

    @Override
    public GuardResult evaluate(TransitionContext context) {
        QaGuardDecision decision = store.findLatestCompletedSummary(
                        context.workItem().organizationId(), context.workItem().id())
                .map(QaGuardDecision::from)
                .orElseGet(QaGuardDecision::noCompletedRun);
        return new GuardResult(decision.missing());
    }
}
