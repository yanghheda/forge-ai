package ai.forge.server.workitem.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.forge.server.workitem.domain.TransitionContext;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RequirementMaterialGuardTest {

    @Test
    void requiresDescriptionAndPublishedPrdInsteadOfStructuredMaterials() {
        RequirementMaterialStore store = mock(RequirementMaterialStore.class);
        when(store.hasPublishedPrd(1, 2)).thenReturn(false);
        RequirementMaterialGuard guard = new RequirementMaterialGuard(store);

        assertThat(guard.evaluate(context("  ")).missing())
                .containsExactly("description", "publishedPrd");
    }

    @Test
    void allowsSubmissionWhenDescriptionAndPublishedPrdExist() {
        RequirementMaterialStore store = mock(RequirementMaterialStore.class);
        when(store.hasPublishedPrd(1, 2)).thenReturn(true);
        RequirementMaterialGuard guard = new RequirementMaterialGuard(store);

        assertThat(guard.evaluate(context("完整需求描述")).allowedTransition()).isTrue();
    }

    private TransitionContext context(String description) {
        WorkItem item = new WorkItem(
                2, 1, 1, "REQ-1", WorkItemType.REQUIREMENT, "需求", description,
                WorkItemStatus.DRAFT, WorkItemPriority.MEDIUM, null, 3, null, 0,
                Instant.parse("2026-09-12T00:00:00Z"), Instant.parse("2026-09-12T00:00:00Z"));
        return new TransitionContext(item, null, null);
    }
}
