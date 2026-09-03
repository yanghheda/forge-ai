package ai.forge.server.workitem.domain;

import java.util.List;

public final class ReasonRequiredGuard implements TransitionGuard {

    @Override
    public GuardResult evaluate(TransitionContext context) {
        return context.reason() == null || context.reason().isBlank()
                ? new GuardResult(List.of("reason"))
                : GuardResult.allowed();
    }
}
