package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.GuardResult;
import ai.forge.server.workitem.domain.TransitionContext;
import ai.forge.server.workitem.domain.TransitionGuard;
import java.util.List;

public final class PublishedPrdGuard implements TransitionGuard {

    /* 查询与 Requirement 同范围关联的已发布 PRD。 */
    private final RequirementMaterialStore materialStore;

    public PublishedPrdGuard(RequirementMaterialStore materialStore) {
        this.materialStore = materialStore;
    }

    @Override
    public GuardResult evaluate(TransitionContext context) {
        boolean published = materialStore.hasPublishedPrd(
                context.workItem().workspaceId(), context.workItem().projectId(), context.workItem().id());
        return published ? GuardResult.allowed() : new GuardResult(List.of("publishedPrd"));
    }
}
