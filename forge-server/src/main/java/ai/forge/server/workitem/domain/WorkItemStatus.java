package ai.forge.server.workitem.domain;

public enum WorkItemStatus {
    /* Requirement 刚创建且尚未进入产品评审的初始状态。 */
    DRAFT,
    /* Requirement 正等待产品角色评审结构化需求材料。 */
    PRODUCT_REVIEW,
    /* Requirement 已通过产品评审并进入体验设计阶段。 */
    UX_IN_PROGRESS,
    /* Requirement 的体验设计交付物正等待评审。 */
    UX_REVIEW,
    /* Requirement 已具备研发开始所需的产品与体验材料。 */
    READY_FOR_DEV,
    /* Requirement 关联的研发任务正在实施。 */
    IN_DEVELOPMENT,
    /* Requirement 的研发交付已具备质量验证条件。 */
    READY_FOR_QA,
    /* Requirement 正在执行质量验证。 */
    IN_QA,
    /* Requirement 已通过质量验证并等待发布。 */
    READY_FOR_RELEASE,
    /* Requirement 对应的交付版本已经发布。 */
    RELEASED,
    /* Requirement 已完成发布后的业务关闭。 */
    DONE,
    /* Requirement 被评审拒绝且不再沿当前流程推进。 */
    REJECTED,
    /* Requirement 被显式取消且保留关联资产和历史。 */
    CANCELLED,
    /* 角色 Task 刚创建且尚未开始处理的初始状态。 */
    TODO
}
