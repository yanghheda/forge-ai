package ai.forge.server.workitem.domain;

public enum WorkItemType {
    /* 承载产品目标与后续交付链根节点的需求工作项。 */
    REQUIREMENT(WorkItemStatus.DRAFT, "requirement"),
    /* 承载用户体验设计工作的角色任务。 */
    UX_TASK(WorkItemStatus.TODO, "ux"),
    /* 承载研发实现工作的角色任务。 */
    DEV_TASK(WorkItemStatus.TODO, "task"),
    /* 承载质量验证工作的角色任务。 */
    QA_TASK(WorkItemStatus.TODO, "task");

    /* 该类型创建时由服务端写入且客户端不能指定的状态。 */
    private final WorkItemStatus initialStatus;

    /* 该类型进行读写授权时使用的稳定权限资源前缀。 */
    private final String permissionResource;

    WorkItemType(WorkItemStatus initialStatus, String permissionResource) {
        this.initialStatus = initialStatus;
        this.permissionResource = permissionResource;
    }

    public WorkItemStatus initialStatus() {
        return initialStatus;
    }

    public String permissionResource() {
        return permissionResource;
    }
}
