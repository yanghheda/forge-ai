package ai.forge.server.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.workitem.domain.WorkflowAction;
import org.junit.jupiter.api.Test;

class AgentStageActionPolicyTest {

    @Test
    void eachSkillCanOnlyRequestItsOwnStageActions() {
        assertThat(AgentStageActionPolicy.allows(
                        AgentSkill.PRODUCT, WorkflowAction.SUBMIT_PRODUCT_REVIEW))
                .isTrue();
        assertThat(AgentStageActionPolicy.allows(
                        AgentSkill.PRODUCT, WorkflowAction.SUBMIT_UX_REVIEW))
                .isFalse();
        assertThat(AgentStageActionPolicy.allows(AgentSkill.UX, WorkflowAction.SUBMIT_UX_REVIEW))
                .isTrue();
        assertThat(AgentStageActionPolicy.allows(
                        AgentSkill.DEVELOPER, WorkflowAction.SUBMIT_FOR_QA))
                .isTrue();
        assertThat(AgentStageActionPolicy.allows(AgentSkill.QA, WorkflowAction.QA_PASS))
                .isTrue();
        assertThat(AgentStageActionPolicy.allows(
                        AgentSkill.RELEASE, WorkflowAction.QA_PASS))
                .isFalse();
    }
}
