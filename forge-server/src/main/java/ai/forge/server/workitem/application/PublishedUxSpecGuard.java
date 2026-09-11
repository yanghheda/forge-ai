package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.GuardResult;
import ai.forge.server.workitem.domain.TransitionContext;
import ai.forge.server.workitem.domain.TransitionGuard;

public final class PublishedUxSpecGuard implements TransitionGuard {

    /* 在提交 UX 阶段评审前检查关联 Requirement 是否存在已发布 UX Spec。 */
    private final RequirementMaterialStore materialStore;

    public PublishedUxSpecGuard(RequirementMaterialStore materialStore) {
        this.materialStore = materialStore;
    }

    @Override
    public GuardResult evaluate(TransitionContext context) {
        boolean present = materialStore.hasPublishedUxSpec(
                context.workItem().organizationId(), context.workItem().id());
        return present ? GuardResult.allowed() : new GuardResult(java.util.List.of("publishedUxSpec"));
    }
}
