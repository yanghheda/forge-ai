package ai.forge.server.workitem.domain;

public enum WorkItemPriority {
    /* 可在更高优先级工作之后安排的低优先级事项。 */
    LOW,
    /* 没有特殊紧迫性时使用的默认业务优先级。 */
    MEDIUM,
    /* 需要优先于常规事项处理的高优先级事项。 */
    HIGH,
    /* 需要团队立即关注的最高业务优先级事项。 */
    URGENT
}
