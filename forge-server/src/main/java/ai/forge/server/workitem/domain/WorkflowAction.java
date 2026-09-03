package ai.forge.server.workitem.domain;

public enum WorkflowAction {
    /* 将材料完整的草稿提交给产品角色评审。 */
    SUBMIT_PRODUCT_REVIEW,
    /* 将产品评审中的 Requirement 退回草稿修改。 */
    REJECT_PRODUCT_REVIEW
}
