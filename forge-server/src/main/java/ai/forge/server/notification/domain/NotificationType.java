package ai.forge.server.notification.domain;

public enum NotificationType {
    /* Agent HIGH 风险 Tool 审批请求，通知公司内候选审批人待处理。 */
    APPROVAL_REQUESTED,
    /* Agent Tool 审批通过或拒绝，通知审批发起人处理结果。 */
    APPROVAL_DECIDED,
    /* 人工发起的部署 HIGH 审批请求，通知公司内候选审批人待处理。 */
    DEPLOYMENT_APPROVAL_REQUESTED,
    /* 部署审批通过或拒绝，通知部署申请人处理结果。 */
    DEPLOYMENT_APPROVAL_DECIDED,
    /* 需求、任务或缺陷负责人被创建指派或改派，通知新负责人。 */
    WORK_ITEM_ASSIGNED,
    /* 需求进入评审等待会签确认，通知对应领域负责人。 */
    REVIEW_REQUESTED,
    /* 阶段交接、QA 回流、退回或验收，通知下一责任方。 */
    REQUIREMENT_HANDOFF;
}
