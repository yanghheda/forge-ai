package ai.forge.server.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.qa.domain.BugAction;
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

    @Test
    void noSkillCanRequestLeaderApprovalActions() {
        var leaderOnly = new WorkflowAction[] {
            WorkflowAction.APPROVE_REQUIREMENT,
            WorkflowAction.REJECT_REQUIREMENT,
            WorkflowAction.APPROVE_PRODUCT_REVIEW,
            WorkflowAction.REJECT_PRODUCT_REVIEW,
            WorkflowAction.APPROVE_UX_REVIEW,
            WorkflowAction.REJECT_UX_REVIEW,
            WorkflowAction.ACCEPT_REQUIREMENT,
            WorkflowAction.APPROVE,
            WorkflowAction.REJECT
        };
        for (AgentSkill skill : AgentSkill.values()) {
            for (WorkflowAction action : leaderOnly) {
                assertThat(AgentStageActionPolicy.allows(skill, action))
                        .as("%s must not allow %s", skill, action)
                        .isFalse();
            }
        }
    }

    @Test
    void taskActionsFollowAssigneeRolesAndExcludeLeaderReview() {
        assertThat(AgentStageActionPolicy.allowsTask(AgentSkill.DEVELOPER, WorkflowAction.START))
                .isTrue();
        assertThat(AgentStageActionPolicy.allowsTask(AgentSkill.QA, WorkflowAction.SUBMIT_REVIEW))
                .isTrue();
        assertThat(AgentStageActionPolicy.allowsTask(AgentSkill.UX, WorkflowAction.BLOCK))
                .isTrue();
        assertThat(AgentStageActionPolicy.allowsTask(AgentSkill.UX, WorkflowAction.UNBLOCK))
                .isTrue();
        // 任务负责人审核属领导审批，永不进入 Agent 白名单（ADR-023 §1、§5）。
        for (AgentSkill skill : AgentSkill.values()) {
            assertThat(AgentStageActionPolicy.allowsTask(skill, WorkflowAction.APPROVE))
                    .as("%s must not approve tasks", skill)
                    .isFalse();
            assertThat(AgentStageActionPolicy.allowsTask(skill, WorkflowAction.REJECT))
                    .as("%s must not reject tasks", skill)
                    .isFalse();
        }
        assertThat(AgentStageActionPolicy.allowsTask(AgentSkill.PRODUCT, WorkflowAction.START))
                .isFalse();
        assertThat(AgentStageActionPolicy.allowsTask(AgentSkill.RELEASE, WorkflowAction.START))
                .isFalse();
    }

    @Test
    void bugActionsAreLimitedToDeveloperAndQaWithoutCancel() {
        assertThat(AgentStageActionPolicy.allowsBug(AgentSkill.DEVELOPER, BugAction.START_FIX))
                .isTrue();
        assertThat(AgentStageActionPolicy.allowsBug(AgentSkill.QA, BugAction.VERIFY))
                .isTrue();
        assertThat(AgentStageActionPolicy.allowsBug(AgentSkill.QA, BugAction.REOPEN))
                .isTrue();
        // 取消属不可达且带破坏性的动作，不开放给 Agent。
        assertThat(AgentStageActionPolicy.allowsBug(AgentSkill.QA, BugAction.CANCEL))
                .isFalse();
        assertThat(AgentStageActionPolicy.allowsBug(AgentSkill.PRODUCT, BugAction.START_FIX))
                .isFalse();
        assertThat(AgentStageActionPolicy.allowsBug(AgentSkill.UX, BugAction.START_FIX))
                .isFalse();
        assertThat(AgentStageActionPolicy.allowsBug(AgentSkill.RELEASE, BugAction.START_FIX))
                .isFalse();
    }
}
