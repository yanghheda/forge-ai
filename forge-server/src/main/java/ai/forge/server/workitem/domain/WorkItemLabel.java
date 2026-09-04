package ai.forge.server.workitem.domain;

public enum WorkItemLabel {
    /* 不包含用户界面的纯后端变更。 */
    BACKEND_ONLY,
    /* 运维、部署或基础设施变更。 */
    OPS,
    /* 仅供内部使用的技术工作。 */
    INTERNAL_TECH
}
