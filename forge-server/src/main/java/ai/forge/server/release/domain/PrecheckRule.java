package ai.forge.server.release.domain;

public enum PrecheckRule {
    /* 所有关联 Requirement 已到达待发布状态。 */
    WORK_ITEMS_READY,
    /* 每个交付项对应 MR head 的最新 Pipeline 成功。 */
    PIPELINE_GREEN,
    /* 每个交付项最新有效 Test Run 已完成且通过。 */
    QA_PASSED,
    /* 不存在仍开放或重开的 BLOCKER/CRITICAL Bug。 */
    NO_BLOCKING_BUGS,
    /* Release Note 与策略要求的文档均已存在。 */
    ARTIFACTS_PRESENT,
    /* HIGH 审批执行前提中的审批人和 TTL 配置合法。 */
    APPROVAL_POLICY
}
