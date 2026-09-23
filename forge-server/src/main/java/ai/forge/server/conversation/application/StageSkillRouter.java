package ai.forge.server.conversation.application;

import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.workitem.domain.WorkItemStatus;

public final class StageSkillRouter {

    private StageSkillRouter() {}

    /* 只接受需求级状态；任务与缺陷状态必须由调用方先上溯到根需求。 */
    public static AgentSkill route(WorkItemStatus status) {
        return switch (status) {
            case DRAFT, REQUIREMENT_APPROVAL, READY_FOR_REVIEW, PRODUCT_REVIEW, IN_ACCEPTANCE ->
                AgentSkill.PRODUCT;
            case READY_FOR_UX, UX_IN_PROGRESS, UX_REVIEW -> AgentSkill.UX;
            case READY_FOR_DEV, IN_DEVELOPMENT -> AgentSkill.DEVELOPER;
            case READY_FOR_QA, IN_QA, IN_FIX_VERIFICATION -> AgentSkill.QA;
            case READY_FOR_RELEASE, RELEASED, DONE -> AgentSkill.RELEASE;
            case WAITING_VERIFICATION, BLOCKED, TODO, IN_PROGRESS, IN_REVIEW, OPEN, RESOLVED, VERIFIED,
                    CLOSED, REJECTED, CANCELLED -> throw new IllegalArgumentException(
                    "work item stage does not support agent conversation");
        };
    }
}
