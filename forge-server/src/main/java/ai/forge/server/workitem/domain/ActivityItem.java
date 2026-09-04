package ai.forge.server.workitem.domain;

import java.time.Instant;

public record ActivityItem(
        /* 投影来源；EVENT 或 COMMENT。 */ String kind,
        /* 来源表内的稳定标识。 */ long id,
        /* 产生本条活动的用户。 */ long actorId,
        /* 工作流活动的动作；评论时为空。 */ WorkflowAction action,
        /* 工作流动作原因；评论时为空。 */ String reason,
        /* 评论正文；工作流事件时为空。 */ String body,
        /* 活动发生的 UTC 时间。 */ Instant createdAt) {}
