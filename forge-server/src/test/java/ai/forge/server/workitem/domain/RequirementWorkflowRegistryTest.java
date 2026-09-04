package ai.forge.server.workitem.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RequirementWorkflowRegistryTest {

    private final TransitionGuard allowingGuard = context -> GuardResult.allowed();
    private final RequirementWorkflowRegistry registry =
            new RequirementWorkflowRegistry(allowingGuard, allowingGuard, allowingGuard);

    @Test
    void registersTheFirstProductActionsWithFixedTargetsAndPermissions() {
        TransitionDefinition submit = registry.require(
                WorkItemType.REQUIREMENT, WorkItemStatus.DRAFT, WorkflowAction.SUBMIT_PRODUCT_REVIEW);
        TransitionDefinition reject = registry.require(
                WorkItemType.REQUIREMENT, WorkItemStatus.PRODUCT_REVIEW, WorkflowAction.REJECT_PRODUCT_REVIEW);
        TransitionDefinition approve = registry.require(
                WorkItemType.REQUIREMENT, WorkItemStatus.PRODUCT_REVIEW, WorkflowAction.APPROVE_PRODUCT_REVIEW);

        assertThat(submit.to()).isEqualTo(WorkItemStatus.PRODUCT_REVIEW);
        assertThat(submit.requiredPermission()).isEqualTo("requirement.edit");
        assertThat(reject.to()).isEqualTo(WorkItemStatus.DRAFT);
        assertThat(reject.requiredPermission()).isEqualTo("requirement.review");
        assertThat(approve.to()).isEqualTo(WorkItemStatus.UX_IN_PROGRESS);
        assertThat(approve.requiredPermission()).isEqualTo("requirement.review");
        assertThat(registry.definitions()).allMatch(definition -> !definition.requiredPermission().isBlank());
    }

    @Test
    void everyRegisteredTargetIsReachableFromTheRequirementInitialState() {
        Set<WorkItemStatus> reachable = Set.of(
                WorkItemStatus.DRAFT, WorkItemStatus.PRODUCT_REVIEW, WorkItemStatus.UX_IN_PROGRESS);
        Set<WorkItemStatus> targets = registry.definitions().stream()
                .map(TransitionDefinition::to)
                .collect(Collectors.toSet());

        assertThat(reachable).containsAll(targets);
    }

    @Test
    void rejectsEveryIllegalFromAndActionCombination() {
        for (WorkItemStatus status : WorkItemStatus.values()) {
            for (WorkflowAction action : WorkflowAction.values()) {
                boolean legal = status == WorkItemStatus.DRAFT && action == WorkflowAction.SUBMIT_PRODUCT_REVIEW
                        || status == WorkItemStatus.PRODUCT_REVIEW
                                && (action == WorkflowAction.REJECT_PRODUCT_REVIEW
                                        || action == WorkflowAction.APPROVE_PRODUCT_REVIEW);
                if (!legal) {
                    assertThatThrownBy(() -> registry.require(WorkItemType.REQUIREMENT, status, action))
                            .isInstanceOf(InvalidTransitionException.class);
                }
            }
        }
    }

    @Test
    void taskTypesCannotUseRequirementActions() {
        assertThatThrownBy(() -> registry.require(
                        WorkItemType.DEV_TASK, WorkItemStatus.DRAFT, WorkflowAction.SUBMIT_PRODUCT_REVIEW))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void resolvesAnActionWithoutRequiringItsOldFromStateForAuthorizedReplay() {
        TransitionDefinition definition = registry.requireAction(
                WorkItemType.REQUIREMENT, WorkflowAction.SUBMIT_PRODUCT_REVIEW);

        assertThat(definition.requiredPermission()).isEqualTo("requirement.edit");
    }
}
