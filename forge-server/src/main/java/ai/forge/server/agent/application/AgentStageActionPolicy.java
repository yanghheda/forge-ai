package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.workitem.domain.WorkflowAction;
import java.util.List;

public final class AgentStageActionPolicy {

    private AgentStageActionPolicy() {}

    public static boolean allows(AgentSkill skill, WorkflowAction action) {
        return switch (skill) {
            case PRODUCT -> List.of(
                            WorkflowAction.SUBMIT_PRODUCT_REVIEW,
                            WorkflowAction.APPROVE_PRODUCT_REVIEW,
                            WorkflowAction.REJECT_PRODUCT_REVIEW)
                    .contains(action);
            case UX -> List.of(
                            WorkflowAction.SUBMIT_UX_REVIEW,
                            WorkflowAction.APPROVE_UX_REVIEW,
                            WorkflowAction.REJECT_UX_REVIEW)
                    .contains(action);
            case DEVELOPER -> action == WorkflowAction.SUBMIT_FOR_QA;
            case QA -> List.of(
                            WorkflowAction.START_QA,
                            WorkflowAction.QA_PASS,
                            WorkflowAction.QA_FAIL)
                    .contains(action);
            case RELEASE -> false;
        };
    }
}
