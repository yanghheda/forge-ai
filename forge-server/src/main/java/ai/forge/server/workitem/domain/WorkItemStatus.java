package ai.forge.server.workitem.domain;

public enum WorkItemStatus {
    /* Requirement 刚创建且尚未进入产品评审的初始状态。 */
    DRAFT,
    /* 角色 Task 刚创建且尚未开始处理的初始状态。 */
    TODO
}
