package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.workitem.domain.WorkflowAction;
import java.util.List;

public final class AgentStageActionPolicy {

    private AgentStageActionPolicy() {}

    public static boolean allows(AgentSkill skill, WorkflowAction action) {
        if (skill != AgentSkill.RELEASE && (action == WorkflowAction.APPROVE_PRODUCT_REVIEW
                || action == WorkflowAction.REJECT_PRODUCT_REVIEW)) return true;
        return switch (skill) {
            case PRODUCT -> List.of(
                            WorkflowAction.SUBMIT_PRODUCT_REVIEW,
                            WorkflowAction.APPROVE_REQUIREMENT,
                            WorkflowAction.REJECT_REQUIREMENT,
                            WorkflowAction.START_PRODUCT_REVIEW,
                            WorkflowAction.ACCEPT_REQUIREMENT,
                            WorkflowAction.APPROVE_PRODUCT_REVIEW,
                            WorkflowAction.REJECT_PRODUCT_REVIEW)
                    .contains(action);
            case UX -> List.of(WorkflowAction.START_UX,
                            WorkflowAction.SUBMIT_UX_REVIEW,
                            WorkflowAction.APPROVE_UX_REVIEW,
                            WorkflowAction.REJECT_UX_REVIEW)
                    .contains(action);
            case DEVELOPER -> action == WorkflowAction.SUBMIT_FOR_QA || action == WorkflowAction.START_DEVELOPMENT;
            case QA -> List.of(
                            WorkflowAction.START_QA,
                            WorkflowAction.QA_PASS,
                            WorkflowAction.QA_FAIL)
                    .contains(action);
            case RELEASE -> false;
        };
    }
}
