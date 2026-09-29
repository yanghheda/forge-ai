package ai.forge.server.notification.application;

import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.organization.application.OrganizationAccessService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test-unit")
public class NotificationService {
    /* 通知标题列宽；写入前截断，避免文案超长导致调用方业务事务失败。 */
    private static final int TITLE_MAX = 255;
    /* 通知正文列宽；写入前截断，避免文案超长导致调用方业务事务失败。 */
    private static final int BODY_MAX = 2000;
    /* 通知读写与收件人反查端口。 */
    private final NotificationStore store;
    /* 解析当前用户唯一公司上下文。 */
    private final OrganizationAccessService access;

    public NotificationService(NotificationStore store, OrganizationAccessService access) {
        this.store = store;
        this.access = access;
    }

    /*
     * 在调用方事务内写入通知：无有效收件人直接返回；收件人按顺序去重并排除操作者本人；
     * 标题与正文按列宽截断后逐收件人写入，任一写入失败随业务事务一并回滚。
     */
    @Transactional
    public void notify(NotificationDraft draft) {
        List<Long> recipients = draft.recipientUserIds();
        if (recipients == null || recipients.isEmpty()) return;
        LinkedHashSet<Long> targets = new LinkedHashSet<>(recipients);
        targets.remove(null);
        if (draft.actorUserId() != null) targets.remove(draft.actorUserId());
        if (targets.isEmpty()) return;
        String title = truncate(draft.title(), TITLE_MAX);
        String body = truncate(draft.body(), BODY_MAX);
        String type = draft.type().name();
        String resourceType = draft.target() == null ? null : draft.target().name();
        for (long userId : targets) {
            store.insert(draft.organizationId(), userId, type, title, body, resourceType, draft.resourceId());
        }
    }

    public List<NotificationView> list(long userId, int limit) {
        long organizationId = organization(userId);
        return store.listByUser(organizationId, userId, Math.max(1, Math.min(limit, 100))).stream().map(row ->
                new NotificationView(number(row, "id"), text(row, "type"), text(row, "title"), text(row, "body"),
                        nullableText(row, "resource_type"), nullableText(row, "resource_id"),
                        instant(row.get("read_at")), instant(row.get("created_at")))).toList();
    }

    public long unread(long userId) {
        long organizationId = organization(userId);
        return store.countUnread(organizationId, userId);
    }

    public void markRead(long userId, long id) {
        long organizationId = organization(userId);
        if (store.markRead(organizationId, userId, id) != 1) throw new ResourceNotFoundException();
    }

    /* 按权限码反查公司内候选收件人，供审批类通知生产方使用。 */
    public List<Long> permissionHolders(long organizationId, String permissionCode) {
        return store.permissionHolders(organizationId, permissionCode);
    }

    /* 按需求领域角色反查负责人，供需求流转交接通知生产方使用。 */
    public List<Long> requirementLeaders(long organizationId, long requirementId, String roleCode) {
        return store.requirementLeaders(organizationId, requirementId, roleCode);
    }

    /* 按需求领域角色反查负责人与执行人并集，供需求流转交接通知生产方使用。 */
    public List<Long> requirementMembers(long organizationId, long requirementId, String roleCode) {
        return store.requirementMembers(organizationId, requirementId, roleCode);
    }

    private long organization(long userId) {
        return access.requireContext(userId).organizationId();
    }

    /* 按列宽截断，空值归一为空字符串以满足 NOT NULL 列约束。 */
    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static String text(Map<String, Object> row, String key) {
        return row.get(key).toString();
    }

    private static String nullableText(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static Instant instant(Object value) {
        return value == null ? null : ((LocalDateTime) value).toInstant(ZoneOffset.UTC);
    }
}
