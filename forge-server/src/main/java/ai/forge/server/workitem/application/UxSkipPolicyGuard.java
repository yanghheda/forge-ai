package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.GuardResult;
import ai.forge.server.workitem.domain.TransitionContext;
import ai.forge.server.workitem.domain.TransitionGuard;
import java.util.ArrayList;
import java.util.List;

public final class UxSkipPolicyGuard implements TransitionGuard {

    /* 读取项目策略与工作项分类这两类服务端事实。 */
    private final RequirementMaterialStore materialStore;

    public UxSkipPolicyGuard(RequirementMaterialStore materialStore) {
        this.materialStore = materialStore;
    }

    @Override
    public GuardResult evaluate(TransitionContext context) {
        List<String> missing = new ArrayList<>();
        if (!materialStore.allowsSkipUx(
                context.workItem().organizationId())) {
            missing.add("organizationPolicy.allowSkipUx");
        }
        if (!materialStore.hasEligibleSkipUxLabel(
                context.workItem().organizationId(),
                context.workItem().id())) {
            missing.add("eligibleSkipUxLabel");
        }
        return missing.isEmpty() ? GuardResult.allowed() : new GuardResult(List.copyOf(missing));
    }
}
