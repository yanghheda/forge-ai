package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.GuardResult;
import ai.forge.server.workitem.domain.TransitionContext;
import ai.forge.server.workitem.domain.TransitionGuard;
import java.util.ArrayList;
import java.util.List;

public final class RequirementMaterialGuard implements TransitionGuard {

    /* 只读查询 Requirement 结构化材料，不在 Guard 中产生副作用。 */
    private final RequirementMaterialStore materialStore;

    public RequirementMaterialGuard(RequirementMaterialStore materialStore) {
        this.materialStore = materialStore;
    }

    @Override
    public GuardResult evaluate(TransitionContext context) {
        RequirementMaterialStore.RequirementMaterial material = materialStore
                .find(context.workItem().workspaceId(), context.workItem().projectId(), context.workItem().id())
                .orElse(new RequirementMaterialStore.RequirementMaterial("", "", 0));
        List<String> missing = new ArrayList<>();
        if (material.goal() == null || material.goal().isBlank()) {
            missing.add("goal");
        }
        if (material.inScope() == null || material.inScope().isBlank()) {
            missing.add("inScope");
        }
        if (material.acceptanceCriteriaCount() == 0) {
            missing.add("acceptanceCriteria");
        }
        return new GuardResult(missing);
    }
}
