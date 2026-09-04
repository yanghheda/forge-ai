package ai.forge.server.workitem.domain;

public enum WorkItemRelationType {
    /* 源工作项依赖目标工作项先完成。 */
    DEPENDS_ON,
    /* 源工作项阻断目标工作项推进。 */
    BLOCKS,
    /* 两个工作项存在一般业务关联。 */
    RELATES_TO
}
