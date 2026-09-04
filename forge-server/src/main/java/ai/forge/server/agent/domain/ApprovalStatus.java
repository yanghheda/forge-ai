package ai.forge.server.agent.domain;

public enum ApprovalStatus {
    /* 等待有权用户决定。 */
    PENDING,
    /* 已批准，等待恢复消费者重新校验。 */
    APPROVED,
    /* 审批人明确拒绝。 */
    REJECTED,
    /* 超过冻结审批的有效时间。 */
    EXPIRED,
    /* Run 取消导致审批失效。 */
    CANCELLED
}
