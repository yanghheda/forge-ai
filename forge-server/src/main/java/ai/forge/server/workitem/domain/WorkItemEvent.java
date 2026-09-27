package ai.forge.server.workitem.domain;

import java.time.Instant;

public record WorkItemEvent(
        /* 事件的单调数据库标识。 */
        long id,
        /* 事件所属工作区。 */
        long organizationId,
        /* 事件所属 Work Item。 */
        long workItemId,
        /* 触发该事件的工作流动作代码；需求、Bug 与发布流程共用同一事件列，取值不限于单一动作枚举。 */
        String action,
        /* 动作前状态。 */
        WorkItemStatus fromStatus,
        /* 动作后状态。 */
        WorkItemStatus toStatus,
        /* 执行动作的用户标识。 */
        long actorId,
        /* 可选的动作原因。 */
        String reason,
        /* 客户端重试使用的幂等键。 */
        String idempotencyKey,
        /* 事件成功提交的 UTC 时间。 */
        Instant createdAt) {}
