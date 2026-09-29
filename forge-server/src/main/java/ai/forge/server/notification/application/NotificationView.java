package ai.forge.server.notification.application;

import java.time.Instant;

/*
 * 通知读取视图，保持与控制台既有响应形状一致（web 已依赖 id/type/title/body/resourceType/resourceId/readAt/createdAt）。
 */
public record NotificationView(
        /* 通知标识。 */ long id,
        /* 通知分类。 */ String type,
        /* 通知标题。 */ String title,
        /* 通知摘要。 */ String body,
        /* 可选关联资源类型；为空表示无跳转目标。 */ String resourceType,
        /* 可选关联资源标识；为空表示无跳转目标。 */ String resourceId,
        /* 首次已读时间；未读时为空。 */ Instant readAt,
        /* 通知创建时间。 */ Instant createdAt) {}
