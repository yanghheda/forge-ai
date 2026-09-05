package ai.forge.server.workitem.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class RequirementWorkflowRegistry {

    /* 本轮可执行的产品动作；后续会话按交付物依赖继续注册。 */
    private final Map<WorkflowAction, TransitionDefinition> definitions;

    public RequirementWorkflowRegistry(
        TransitionGuard requirementMaterialGuard,
        TransitionGuard publishedPrdGuard,
        TransitionGuard publishedUxSpecGuard,
        TransitionGuard uxChecklistGuard,
        TransitionGuard skipUxPolicyGuard,
        TransitionGuard reasonGuard,
        TransitionGuard developmentQaGuard) {
        EnumMap<WorkflowAction, TransitionDefinition> registered = new EnumMap<>(WorkflowAction.class);
        register(registered, new TransitionDefinition(
            WorkItemType.REQUIREMENT,
            WorkItemStatus.PRODUCT_REVIEW,
            WorkflowAction.APPROVE_PRODUCT_REVIEW,
            WorkItemStatus.UX_IN_PROGRESS,
            "requirement.review",
            List.of(publishedPrdGuard)));
        register(registered, new TransitionDefinition(
            WorkItemType.REQUIREMENT,
            WorkItemStatus.DRAFT,
            WorkflowAction.SUBMIT_PRODUCT_REVIEW,
            WorkItemStatus.PRODUCT_REVIEW,
            "requirement.edit",
            List.of(requirementMaterialGuard)));
        register(registered, new TransitionDefinition(
            WorkItemType.REQUIREMENT,
            WorkItemStatus.PRODUCT_REVIEW,
            WorkflowAction.REJECT_PRODUCT_REVIEW,
            WorkItemStatus.DRAFT,
            "requirement.review",
            List.of(reasonGuard)));
        register(registered, new TransitionDefinition(
            WorkItemType.REQUIREMENT,
            WorkItemStatus.UX_IN_PROGRESS,
            WorkflowAction.SUBMIT_UX_REVIEW,
            WorkItemStatus.UX_REVIEW,
            "ux.edit",
            List.of(publishedUxSpecGuard, uxChecklistGuard)));
        register(registered, new TransitionDefinition(
            WorkItemType.REQUIREMENT,
            WorkItemStatus.UX_REVIEW,
            WorkflowAction.APPROVE_UX_REVIEW,
            WorkItemStatus.READY_FOR_DEV,
            "ux.review",
            List.of()));
        register(registered, new TransitionDefinition(
            WorkItemType.REQUIREMENT,
            WorkItemStatus.UX_REVIEW,
            WorkflowAction.REJECT_UX_REVIEW,
            WorkItemStatus.UX_IN_PROGRESS,
            "ux.review",
            List.of(reasonGuard)));
        register(registered, new TransitionDefinition(
            WorkItemType.REQUIREMENT,
            WorkItemStatus.PRODUCT_REVIEW,
            WorkflowAction.SKIP_UX,
            WorkItemStatus.READY_FOR_DEV,
            "ux.skip",
            List.of(skipUxPolicyGuard, reasonGuard)));
        register(registered, new TransitionDefinition(
            WorkItemType.REQUIREMENT,
            WorkItemStatus.IN_DEVELOPMENT,
            WorkflowAction.SUBMIT_FOR_QA,
            WorkItemStatus.READY_FOR_QA,
            "development.submit",
            List.of(developmentQaGuard)));
        register(registered, new TransitionDefinition(
            WorkItemType.UX_TASK,
            WorkItemStatus.TODO,
            WorkflowAction.START,
            WorkItemStatus.IN_PROGRESS,
            "ux.edit",
            List.of()));
        register(registered, new TransitionDefinition(
            WorkItemType.UX_TASK,
            WorkItemStatus.IN_PROGRESS,
            WorkflowAction.SUBMIT_REVIEW,
            WorkItemStatus.IN_REVIEW,
            "ux.edit",
            List.of()));
        register(registered, new TransitionDefinition(
            WorkItemType.UX_TASK,
            WorkItemStatus.IN_REVIEW,
            WorkflowAction.APPROVE,
            WorkItemStatus.DONE,
            "ux.review",
            List.of()));
        register(registered, new TransitionDefinition(
            WorkItemType.UX_TASK,
            WorkItemStatus.IN_REVIEW,
            WorkflowAction.REJECT,
            WorkItemStatus.IN_PROGRESS,
            "ux.review",
            List.of(reasonGuard)));
        definitions = Map.copyOf(registered);
    }

    public TransitionDefinition require(WorkItemType type, WorkItemStatus from, WorkflowAction action) {
        TransitionDefinition definition = findByAction(action);
        if (definition == null || definition.type() != type || definition.from() != from) {
            throw new InvalidTransitionException();
        }
        return definition;
    }

    public TransitionDefinition requireAction(WorkItemType type, WorkflowAction action) {
        TransitionDefinition definition = findByAction(action);
        if (definition == null || definition.type() != type) {
            throw new InvalidTransitionException();
        }
        return definition;
    }

    public List<TransitionDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    private void register(EnumMap<WorkflowAction, TransitionDefinition> registered, TransitionDefinition definition) {
        if (definition.requiredPermission() == null || definition.requiredPermission().isBlank()) {
            throw new IllegalArgumentException("Workflow permission must not be blank");
        }
        if (registered.putIfAbsent(definition.action(), definition) != null) {
            throw new IllegalArgumentException("Workflow action must be unique");
        }
    }

    private TransitionDefinition findByAction(WorkflowAction action) {
        return definitions.get(action);
    }
}
