package ai.forge.server.notification.application;

import ai.forge.server.notification.domain.NotificationTarget;
import ai.forge.server.notification.domain.NotificationType;
import java.util.List;

/*
 * 一次通知写入意图；由业务变更方在同一事务内构造后交给 NotificationService 统一落库，
 * 收件人去重、排除操作者与列宽截断均在服务端完成。
 */
public record NotificationDraft(
        /* 通知所属公司，写入时用于公司范围隔离。 */
        long organizationId,
        /* 收件人候选用户标识，写入前去重并排除操作者本人。 */
        List<Long> recipientUserIds,
        /* 触发本次通知的操作者标识；无明确操作者时可为空。 */
        Long actorUserId,
        /* 通知业务分类，写入时取枚举名落库。 */
        NotificationType type,
        /* 通知标题，写入时按列宽截断至 255。 */
        String title,
        /* 通知正文摘要，写入时按列宽截断至 2000。 */
        String body,
        /* 通知点击后的 web 跳转目标类型，写入时取枚举名落库。 */
        NotificationTarget target,
        /* 跳转资源标识；无可跳转资源时为空。 */
        String resourceId) {}
