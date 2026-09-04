package ai.forge.server.workitem.domain;

public enum WorkflowAction {
    /* 将材料完整的草稿提交给产品角色评审。 */
    SUBMIT_PRODUCT_REVIEW,
    /* 批准已具备发布 PRD 的产品评审并进入 UX 阶段。 */
    APPROVE_PRODUCT_REVIEW,
    /* 将产品评审中的 Requirement 退回草稿修改。 */
    REJECT_PRODUCT_REVIEW,
    /* 提交已发布 UX Spec 与完整交付检查清单供阶段评审。 */
    SUBMIT_UX_REVIEW,
    /* 批准 UX 阶段评审并允许 Requirement 进入研发准备状态。 */
    APPROVE_UX_REVIEW,
    /* 将 UX 阶段评审退回到交付物补充状态。 */
    REJECT_UX_REVIEW,
    /* 依据项目策略与显式分类跳过 UX，并保留必填原因。 */
    SKIP_UX,
    /* 开始处理角色 Task。 */
    START,
    /* 将 UX Task 提交到其局部评审队列。 */
    SUBMIT_REVIEW,
    /* 批准 UX Task 局部评审并完成该 Task。 */
    APPROVE,
    /* 退回 UX Task 局部评审。 */
    REJECT
}
