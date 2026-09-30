package ai.forge.server.notification.application;

import java.util.List;
import java.util.Map;

/*
 * 通知读写与收件人反查端口；实现位于 notification.infrastructure.persistence，
 * 所有查询显式携带 organization_id，公司范围隔离由 SQL 保证。
 */
public interface NotificationStore {
    List<Map<String, Object>> listByUser(long organizationId, long userId, int limit);
    List<Map<String, Object>> findAfter(long organizationId, long userId, long afterId, int limit);
    long countUnread(long organizationId, long userId);
    int markRead(long organizationId, long userId, long id);
    void insert(long organizationId, long userId, String type, String title, String body, String resourceType,
            String resourceId);
    List<Long> permissionHolders(long organizationId, String permissionCode);
    List<Long> requirementLeaders(long organizationId, long requirementId, String roleCode);
    List<Long> requirementMembers(long organizationId, long requirementId, String roleCode);
}
