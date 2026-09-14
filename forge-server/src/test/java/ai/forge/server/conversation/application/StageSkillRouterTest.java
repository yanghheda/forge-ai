package ai.forge.server.conversation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.workitem.domain.WorkItemStatus;
import org.junit.jupiter.api.Test;

class StageSkillRouterTest {

    @Test
    void routesEveryRequirementStageToItsResponsibleSkill() {
        assertThat(StageSkillRouter.route(WorkItemStatus.DRAFT)).isEqualTo(AgentSkill.PRODUCT);
        assertThat(StageSkillRouter.route(WorkItemStatus.PRODUCT_REVIEW)).isEqualTo(AgentSkill.PRODUCT);
        assertThat(StageSkillRouter.route(WorkItemStatus.UX_IN_PROGRESS)).isEqualTo(AgentSkill.UX);
        assertThat(StageSkillRouter.route(WorkItemStatus.UX_REVIEW)).isEqualTo(AgentSkill.UX);
        assertThat(StageSkillRouter.route(WorkItemStatus.READY_FOR_DEV)).isEqualTo(AgentSkill.DEVELOPER);
        assertThat(StageSkillRouter.route(WorkItemStatus.IN_DEVELOPMENT)).isEqualTo(AgentSkill.DEVELOPER);
        assertThat(StageSkillRouter.route(WorkItemStatus.READY_FOR_QA)).isEqualTo(AgentSkill.QA);
        assertThat(StageSkillRouter.route(WorkItemStatus.IN_QA)).isEqualTo(AgentSkill.QA);
        assertThat(StageSkillRouter.route(WorkItemStatus.READY_FOR_RELEASE)).isEqualTo(AgentSkill.RELEASE);
        assertThat(StageSkillRouter.route(WorkItemStatus.RELEASED)).isEqualTo(AgentSkill.RELEASE);
    }

    @Test
    void rejectsTaskAndBugStatuses() {
        assertThatThrownBy(() -> StageSkillRouter.route(WorkItemStatus.TODO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
