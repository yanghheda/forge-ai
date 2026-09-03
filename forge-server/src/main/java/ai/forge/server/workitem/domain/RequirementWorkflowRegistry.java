package ai.forge.server.workitem.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class RequirementWorkflowRegistry {

    /* 本轮可执行的产品动作；后续会话按交付物依赖继续注册。 */
    private final Map<WorkflowAction, TransitionDefinition> definitions;

    public RequirementWorkflowRegistry(TransitionGuard requirementMaterialGuard, TransitionGuard reasonGuard) {
        EnumMap<WorkflowAction, TransitionDefinition> registered = new EnumMap<>(WorkflowAction.class);
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

    private void register(
            EnumMap<WorkflowAction, TransitionDefinition> registered, TransitionDefinition definition) {
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
