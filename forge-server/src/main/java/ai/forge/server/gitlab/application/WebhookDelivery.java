package ai.forge.server.gitlab.application;

import java.time.LocalDateTime;

public record WebhookDelivery(
        /* 本地 delivery 稳定标识。 */ long id,
        /* 事件所属工作区范围。 */ long workspaceId,
        /* 接收事件的 GitLab 连接。 */ long connectionId,
        /* 远端 UUID 或确定性 fallback key。 */ String deliveryKey,
        /* GitLab 事件类型 Header。 */ String eventType,
        /* 完整受限 payload 的 SHA-256 摘要。 */ String payloadHash,
        /* 待异步处理的短期原始 JSON；处理完成后清空。 */ String payload,
        /* 当前处理尝试次数。 */ int attempts,
        /* 接收事件的 UTC 时间。 */ LocalDateTime receivedAt) {}
