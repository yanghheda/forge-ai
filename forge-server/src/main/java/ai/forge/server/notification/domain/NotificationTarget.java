package ai.forge.server.notification.domain;

/*
 * 通知点击后 web 端的跳转目标类型，作为 notifications.resource_type 的取值落库；
 * 与储存在 resource_id 中的业务资源标识共同决定前端深链目标。
 */
public enum NotificationTarget {
    /* 跳转到需求详情页。 */
    REQUIREMENT,
    /* 跳转到需求 UX 阶段页。 */
    REQUIREMENT_UX,
    /* 跳转到需求开发阶段页。 */
    REQUIREMENT_DEVELOPMENT,
    /* 跳转到需求 QA 阶段页。 */
    REQUIREMENT_QA,
    /* 跳转到需求发布阶段页。 */
    REQUIREMENT_RELEASE,
    /* 跳转到缺陷详情页。 */
    DEFECT,
    /* 跳转到待审批页面。 */
    APPROVAL,
    /* 跳转到 Agent 执行轨迹页。 */
    AGENT_RUN;
}
