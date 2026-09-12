package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.GuardResult;
import ai.forge.server.workitem.domain.TransitionContext;
import ai.forge.server.workitem.domain.TransitionGuard;
import java.util.List;

public final class RequirementMaterialGuard implements TransitionGuard {

    /* 查询与 Requirement 关联的已发布 PRD，不在 Guard 中产生副作用。 */
    private final RequirementMaterialStore materialStore;

    public RequirementMaterialGuard(RequirementMaterialStore materialStore) {
        this.materialStore = materialStore;
    }

    @Override
    public GuardResult evaluate(TransitionContext context) {
        List<String> missing = new java.util.ArrayList<>();
        if (context.workItem().description() == null || context.workItem().description().isBlank()) {
            missing.add("description");
        }
        if (!materialStore.hasPublishedPrd(
                context.workItem().organizationId(), context.workItem().id())) {
            missing.add("publishedPrd");
        }
        return new GuardResult(missing);
    }
}
