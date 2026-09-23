package ai.forge.server.conversation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.workitem.domain.WorkItemStatus;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class StageSkillRouterTest {

    @Test
    void routesEveryRequirementStageToItsResponsibleSkill() {
        assertThat(StageSkillRouter.route(WorkItemStatus.DRAFT)).isEqualTo(AgentSkill.PRODUCT);
        assertThat(StageSkillRouter.route(WorkItemStatus.REQUIREMENT_APPROVAL)).isEqualTo(AgentSkill.PRODUCT);
        assertThat(StageSkillRouter.route(WorkItemStatus.READY_FOR_REVIEW)).isEqualTo(AgentSkill.PRODUCT);
        assertThat(StageSkillRouter.route(WorkItemStatus.PRODUCT_REVIEW)).isEqualTo(AgentSkill.PRODUCT);
        assertThat(StageSkillRouter.route(WorkItemStatus.READY_FOR_UX)).isEqualTo(AgentSkill.UX);
        assertThat(StageSkillRouter.route(WorkItemStatus.UX_IN_PROGRESS)).isEqualTo(AgentSkill.UX);
        assertThat(StageSkillRouter.route(WorkItemStatus.UX_REVIEW)).isEqualTo(AgentSkill.UX);
        assertThat(StageSkillRouter.route(WorkItemStatus.READY_FOR_DEV)).isEqualTo(AgentSkill.DEVELOPER);
        assertThat(StageSkillRouter.route(WorkItemStatus.IN_DEVELOPMENT)).isEqualTo(AgentSkill.DEVELOPER);
        assertThat(StageSkillRouter.route(WorkItemStatus.READY_FOR_QA)).isEqualTo(AgentSkill.QA);
        assertThat(StageSkillRouter.route(WorkItemStatus.IN_QA)).isEqualTo(AgentSkill.QA);
        assertThat(StageSkillRouter.route(WorkItemStatus.IN_FIX_VERIFICATION)).isEqualTo(AgentSkill.QA);
        assertThat(StageSkillRouter.route(WorkItemStatus.IN_ACCEPTANCE)).isEqualTo(AgentSkill.PRODUCT);
        assertThat(StageSkillRouter.route(WorkItemStatus.READY_FOR_RELEASE)).isEqualTo(AgentSkill.RELEASE);
        assertThat(StageSkillRouter.route(WorkItemStatus.RELEASED)).isEqualTo(AgentSkill.RELEASE);
        assertThat(StageSkillRouter.route(WorkItemStatus.DONE)).isEqualTo(AgentSkill.RELEASE);
    }

    @Test
    void everyRequirementStatusIsEitherRoutedOrRejected() {
        var routed = Arrays.asList(
                WorkItemStatus.DRAFT,
                WorkItemStatus.REQUIREMENT_APPROVAL,
                WorkItemStatus.READY_FOR_REVIEW,
                WorkItemStatus.PRODUCT_REVIEW,
                WorkItemStatus.READY_FOR_UX,
                WorkItemStatus.UX_IN_PROGRESS,
                WorkItemStatus.UX_REVIEW,
                WorkItemStatus.READY_FOR_DEV,
                WorkItemStatus.IN_DEVELOPMENT,
                WorkItemStatus.READY_FOR_QA,
                WorkItemStatus.IN_QA,
                WorkItemStatus.IN_FIX_VERIFICATION,
                WorkItemStatus.IN_ACCEPTANCE,
                WorkItemStatus.READY_FOR_RELEASE,
                WorkItemStatus.RELEASED,
                WorkItemStatus.DONE);
        var rejected = Arrays.asList(
                WorkItemStatus.WAITING_VERIFICATION,
                WorkItemStatus.BLOCKED,
                WorkItemStatus.TODO,
                WorkItemStatus.IN_PROGRESS,
                WorkItemStatus.IN_REVIEW,
                WorkItemStatus.OPEN,
                WorkItemStatus.RESOLVED,
                WorkItemStatus.VERIFIED,
                WorkItemStatus.CLOSED,
                WorkItemStatus.REJECTED,
                WorkItemStatus.CANCELLED);

        assertThat(routed.size() + rejected.size()).isEqualTo(WorkItemStatus.values().length);
        assertThat(routed).doesNotContainAnyElementsOf(rejected);
    }

    @Test
    void rejectsTaskAndBugStatuses() {
        assertThatThrownBy(() -> StageSkillRouter.route(WorkItemStatus.TODO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StageSkillRouter.route(WorkItemStatus.IN_PROGRESS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StageSkillRouter.route(WorkItemStatus.WAITING_VERIFICATION))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StageSkillRouter.route(WorkItemStatus.OPEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StageSkillRouter.route(WorkItemStatus.VERIFIED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
