package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.qa.domain.BugAction;
import ai.forge.server.workitem.domain.WorkflowAction;
import java.util.List;

public final class AgentStageActionPolicy {

    private AgentStageActionPolicy() {}

    /* 领导审批动作永不开放给 Agent：四领域会签票、需求审核与驳回、UX 评审通过与驳回、验收（ADR-023 §1）。 */
    public static boolean allows(AgentSkill skill, WorkflowAction action) {
        return switch (skill) {
            case PRODUCT -> List.of(WorkflowAction.SUBMIT_PRODUCT_REVIEW, WorkflowAction.START_PRODUCT_REVIEW)
                    .contains(action);
            case UX -> List.of(WorkflowAction.START_UX, WorkflowAction.SUBMIT_UX_REVIEW).contains(action);
            case DEVELOPER -> action == WorkflowAction.SUBMIT_FOR_QA || action == WorkflowAction.START_DEVELOPMENT;
            case QA -> List.of(
                            WorkflowAction.START_QA,
                            WorkflowAction.QA_PASS,
                            WorkflowAction.QA_FAIL)
                    .contains(action);
            case RELEASE -> false;
        };
    }

    /* 任务动作按角色收敛；审核动作 APPROVE/REJECT 属任务负责人专属，永不进入白名单（ADR-023 §5）。 */
    public static boolean allowsTask(AgentSkill skill, WorkflowAction action) {
        if (action == WorkflowAction.APPROVE || action == WorkflowAction.REJECT) return false;
        if (skill != AgentSkill.UX && skill != AgentSkill.QA && skill != AgentSkill.DEVELOPER) return false;
        /* 开发任务的开工会创建分支与合并请求、提交验证要求 MR 已合并：分别由 start_development Tool 与人工完成开发任务承担，Agent 只保留阻塞与解除阻塞。 */
        if (skill == AgentSkill.DEVELOPER)
            return action == WorkflowAction.BLOCK || action == WorkflowAction.UNBLOCK;
        return List.of(
                        WorkflowAction.START,
                        WorkflowAction.SUBMIT_REVIEW,
                        WorkflowAction.BLOCK,
                        WorkflowAction.UNBLOCK)
                .contains(action);
    }

    /* 缺陷修复与验证的真实授权是「修复人 / QA 领域成员」，由 BugService 复核，因此 Skill 只排除无关角色。 */
    public static boolean allowsBug(AgentSkill skill, BugAction action) {
        if (action == BugAction.CANCEL) return false;
        return switch (skill) {
            case DEVELOPER, QA -> true;
            case PRODUCT, UX, RELEASE -> false;
        };
    }
}
