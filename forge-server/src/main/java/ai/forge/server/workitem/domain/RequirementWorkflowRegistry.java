package ai.forge.server.workitem.domain;

import java.util.ArrayList;
import java.util.List;

public final class RequirementWorkflowRegistry {
    /* 类型、来源状态与动作共同确定唯一转换，避免任务之间相互覆盖。 */
    private final List<TransitionDefinition> definitions;

    public RequirementWorkflowRegistry(TransitionGuard requirementMaterialGuard, TransitionGuard publishedPrdGuard,
            TransitionGuard publishedUxSpecGuard, TransitionGuard uxChecklistGuard, TransitionGuard skipUxPolicyGuard,
            TransitionGuard reasonGuard, TransitionGuard developmentQaGuard, TransitionGuard qaPassGuard) {
        List<TransitionDefinition> rules = new ArrayList<>();
        requirement(rules, WorkItemStatus.DRAFT, WorkflowAction.SUBMIT_PRODUCT_REVIEW, WorkItemStatus.REQUIREMENT_APPROVAL,
                "requirement.edit", requirementMaterialGuard);
        requirement(rules, WorkItemStatus.REQUIREMENT_APPROVAL, WorkflowAction.APPROVE_REQUIREMENT, WorkItemStatus.READY_FOR_REVIEW,
                "requirement.approve", publishedPrdGuard);
        requirement(rules, WorkItemStatus.REQUIREMENT_APPROVAL, WorkflowAction.REJECT_REQUIREMENT, WorkItemStatus.DRAFT,
                "requirement.approve", reasonGuard);
        requirement(rules, WorkItemStatus.READY_FOR_REVIEW, WorkflowAction.START_PRODUCT_REVIEW, WorkItemStatus.PRODUCT_REVIEW,
                "requirement.edit", publishedPrdGuard);
        requirement(rules, WorkItemStatus.PRODUCT_REVIEW, WorkflowAction.APPROVE_PRODUCT_REVIEW, WorkItemStatus.READY_FOR_UX,
                "requirement.read", publishedPrdGuard);
        requirement(rules, WorkItemStatus.PRODUCT_REVIEW, WorkflowAction.REJECT_PRODUCT_REVIEW, WorkItemStatus.DRAFT,
                "requirement.read", reasonGuard);
        requirement(rules, WorkItemStatus.READY_FOR_UX, WorkflowAction.START_UX, WorkItemStatus.UX_IN_PROGRESS, "ux.edit");
        requirement(rules, WorkItemStatus.UX_IN_PROGRESS, WorkflowAction.SUBMIT_UX_REVIEW, WorkItemStatus.UX_REVIEW,
                "ux.edit", publishedUxSpecGuard, uxChecklistGuard);
        requirement(rules, WorkItemStatus.UX_REVIEW, WorkflowAction.APPROVE_UX_REVIEW, WorkItemStatus.READY_FOR_DEV, "ux.approve");
        requirement(rules, WorkItemStatus.UX_REVIEW, WorkflowAction.REJECT_UX_REVIEW, WorkItemStatus.UX_IN_PROGRESS,
                "ux.approve", reasonGuard);
        requirement(rules, WorkItemStatus.READY_FOR_DEV, WorkflowAction.START_DEVELOPMENT, WorkItemStatus.IN_DEVELOPMENT, "task.edit");
        requirement(rules, WorkItemStatus.IN_DEVELOPMENT, WorkflowAction.SUBMIT_FOR_QA, WorkItemStatus.READY_FOR_QA,
                "development.submit", developmentQaGuard);
        requirement(rules, WorkItemStatus.READY_FOR_QA, WorkflowAction.START_QA, WorkItemStatus.IN_QA, "qa.execute");
        requirement(rules, WorkItemStatus.IN_QA, WorkflowAction.QA_FAIL, WorkItemStatus.IN_FIX_VERIFICATION, "qa.execute");
        requirement(rules, WorkItemStatus.IN_QA, WorkflowAction.QA_PASS, WorkItemStatus.IN_ACCEPTANCE, "qa.execute", qaPassGuard);
        requirement(rules, WorkItemStatus.IN_FIX_VERIFICATION, WorkflowAction.QA_PASS, WorkItemStatus.IN_ACCEPTANCE, "qa.execute", qaPassGuard);
        requirement(rules, WorkItemStatus.IN_ACCEPTANCE, WorkflowAction.ACCEPT_REQUIREMENT, WorkItemStatus.READY_FOR_RELEASE, "requirement.approve");
        for (WorkItemType type : List.of(WorkItemType.UX_TASK, WorkItemType.DEV_TASK, WorkItemType.QA_TASK)) {
            String permission = type.permissionResource() + ".edit";
            task(rules, type, WorkItemStatus.TODO, WorkflowAction.START, WorkItemStatus.IN_PROGRESS, permission);
            task(rules, type, WorkItemStatus.IN_PROGRESS, WorkflowAction.SUBMIT_REVIEW, WorkItemStatus.WAITING_VERIFICATION, permission);
            task(rules, type, WorkItemStatus.WAITING_VERIFICATION, WorkflowAction.APPROVE, WorkItemStatus.DONE, permission);
            task(rules, type, WorkItemStatus.WAITING_VERIFICATION, WorkflowAction.REJECT, WorkItemStatus.IN_PROGRESS, permission, reasonGuard);
            task(rules, type, WorkItemStatus.IN_PROGRESS, WorkflowAction.BLOCK, WorkItemStatus.BLOCKED, permission, reasonGuard);
            task(rules, type, WorkItemStatus.BLOCKED, WorkflowAction.UNBLOCK, WorkItemStatus.IN_PROGRESS, permission, reasonGuard);
        }
        definitions = List.copyOf(rules);
    }

    public TransitionDefinition require(WorkItemType type, WorkItemStatus from, WorkflowAction action) {
        return definitions.stream().filter(d -> d.type() == type && d.from() == from && d.action() == action)
                .findFirst().orElseThrow(InvalidTransitionException::new);
    }

    public TransitionDefinition requireAction(WorkItemType type, WorkflowAction action) {
        return definitions.stream().filter(d -> d.type() == type && d.action() == action)
                .findFirst().orElseThrow(InvalidTransitionException::new);
    }

    public List<TransitionDefinition> definitions() { return definitions; }

    private void requirement(List<TransitionDefinition> rules, WorkItemStatus from, WorkflowAction action,
            WorkItemStatus to, String permission, TransitionGuard... guards) {
        task(rules, WorkItemType.REQUIREMENT, from, action, to, permission, guards);
    }

    private void task(List<TransitionDefinition> rules, WorkItemType type, WorkItemStatus from,
            WorkflowAction action, WorkItemStatus to, String permission, TransitionGuard... guards) {
        rules.add(new TransitionDefinition(type, from, action, to, permission, List.of(guards)));
    }
}
