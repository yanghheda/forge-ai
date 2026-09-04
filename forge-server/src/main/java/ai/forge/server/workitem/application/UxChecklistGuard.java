package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.GuardResult;
import ai.forge.server.workitem.domain.TransitionContext;
import ai.forge.server.workitem.domain.TransitionGuard;
import java.util.LinkedHashSet;
import java.util.List;

public final class UxChecklistGuard implements TransitionGuard {

    /* UX 阶段准入必须明确确认的四类交付物。 */
    private static final List<String> REQUIRED = List.of("userFlow", "pageList", "keyInteraction", "exceptionState");

    @Override
    public GuardResult evaluate(TransitionContext context) {
        LinkedHashSet<String> submitted = new LinkedHashSet<>(
                context.checklist() == null ? List.of() : context.checklist());
        List<String> missing = REQUIRED.stream().filter(item -> !submitted.contains(item)).toList();
        return new GuardResult(missing);
    }
}
