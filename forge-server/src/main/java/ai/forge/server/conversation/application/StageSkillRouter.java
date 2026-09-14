package ai.forge.server.conversation.application;

import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.workitem.domain.WorkItemStatus;

public final class StageSkillRouter {

    private StageSkillRouter() {}

    public static AgentSkill route(WorkItemStatus status) {
        return switch (status) {
            case DRAFT, PRODUCT_REVIEW -> AgentSkill.PRODUCT;
            case UX_IN_PROGRESS, UX_REVIEW -> AgentSkill.UX;
            case READY_FOR_DEV, IN_DEVELOPMENT -> AgentSkill.DEVELOPER;
            case READY_FOR_QA, IN_QA -> AgentSkill.QA;
            case READY_FOR_RELEASE, RELEASED, DONE -> AgentSkill.RELEASE;
            default -> throw new IllegalArgumentException(
                    "work item stage does not support agent conversation");
        };
    }
}
