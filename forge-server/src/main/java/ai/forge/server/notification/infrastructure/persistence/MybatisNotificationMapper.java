package ai.forge.server.notification.infrastructure.persistence;

import ai.forge.server.notification.application.NotificationStore;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/*
 * notifications 表的唯一读写入口；所有 SQL 显式携带 organization_id，收件人反查只返回
 * organization_members.status='ACTIVE' 与 users.status='ACTIVE' 的公司有效成员。
 */
@Mapper
public interface MybatisNotificationMapper extends NotificationStore {

    @Select("SELECT id,type,title,body,resource_type,resource_id,read_at,created_at FROM notifications "
            + "WHERE organization_id=#{organizationId} AND user_id=#{userId} ORDER BY created_at DESC,id DESC LIMIT #{limit}")
    List<Map<String, Object>> listByUser(@Param("organizationId") long organizationId,
            @Param("userId") long userId, @Param("limit") int limit);

    /*
     * SSE 事件流游标补发：只返回 id 大于游标的本人通知，按 id 升序逐批读取，
     * 保证同一用户按 id 至少一次、不重复推送；公司范围与用户隔离由 SQL 保证。
     */
    @Select("SELECT id,type,title,body,resource_type,resource_id,read_at,created_at FROM notifications "
            + "WHERE organization_id=#{organizationId} AND user_id=#{userId} AND id > #{afterId} "
            + "ORDER BY id ASC LIMIT #{limit}")
    List<Map<String, Object>> findAfter(@Param("organizationId") long organizationId,
            @Param("userId") long userId, @Param("afterId") long afterId, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM notifications WHERE organization_id=#{organizationId} AND user_id=#{userId} AND read_at IS NULL")
    long countUnread(@Param("organizationId") long organizationId, @Param("userId") long userId);

    /*
     * 幂等标记已读：COALESCE 保留首次已读时间，重复调用不改变 read_at；
     * 同时限定 id + organization_id + user_id，越权或不存在时影响行数为 0。
     */
    @Update("UPDATE notifications SET read_at=COALESCE(read_at,UTC_TIMESTAMP(6)) WHERE id=#{id} "
            + "AND organization_id=#{organizationId} AND user_id=#{userId}")
    int markRead(@Param("organizationId") long organizationId, @Param("userId") long userId, @Param("id") long id);

    @Insert("INSERT INTO notifications(organization_id,user_id,type,title,body,resource_type,resource_id,created_at) "
            + "VALUES(#{organizationId},#{userId},#{type},#{title},#{body},#{resourceType},#{resourceId},UTC_TIMESTAMP(6))")
    void insert(@Param("organizationId") long organizationId, @Param("userId") long userId,
            @Param("type") String type, @Param("title") String title, @Param("body") String body,
            @Param("resourceType") String resourceType, @Param("resourceId") String resourceId);

    @Select("SELECT DISTINCT om.user_id FROM organization_members om "
            + "JOIN users u ON u.id=om.user_id AND u.status='ACTIVE' "
            + "JOIN member_roles mr ON mr.organization_member_id=om.id "
            + "JOIN role_permissions rp ON rp.role_id=mr.role_id "
            + "JOIN permissions p ON p.id=rp.permission_id "
            + "WHERE om.organization_id=#{organizationId} AND om.status='ACTIVE' AND p.code=#{permissionCode} "
            + "ORDER BY om.user_id")
    List<Long> permissionHolders(@Param("organizationId") long organizationId,
            @Param("permissionCode") String permissionCode);

    /*
     * 需求领域负责人反查：requirement_participants 中持有 {role}_ADMIN 的公司有效成员，
     * 语义与 DeliveryWorkflowMapper.isLeader 一致。
     */
    @Select("SELECT DISTINCT rp.user_id FROM requirement_participants rp "
            + "JOIN organization_members om ON om.user_id=rp.user_id AND om.organization_id=#{organizationId} "
            + "JOIN users u ON u.id=om.user_id AND u.status='ACTIVE' "
            + "JOIN member_roles mr ON mr.organization_member_id=om.id "
            + "JOIN roles r ON r.id=mr.role_id "
            + "WHERE rp.organization_id=#{organizationId} AND rp.requirement_id=#{requirementId} "
            + "AND rp.role_code=#{roleCode} AND om.status='ACTIVE' AND r.code=CONCAT(#{roleCode},'_ADMIN') "
            + "ORDER BY rp.user_id")
    List<Long> requirementLeaders(@Param("organizationId") long organizationId,
            @Param("requirementId") long requirementId, @Param("roleCode") String roleCode);

    /*
     * 需求领域成员反查：领域负责人（requirement_participants ⋈ {role}_ADMIN）与执行人
     * （requirement_executors 中成员角色为 role 或 role_ADMIN）的并集，执行人语义与
     * DeliveryWorkflowMapper.isExecutor 一致；UNION 去重后按 user_id 升序。
     */
    @Select("SELECT user_id FROM ("
            + "SELECT DISTINCT rp.user_id FROM requirement_participants rp "
            + "JOIN organization_members om ON om.user_id=rp.user_id AND om.organization_id=#{organizationId} "
            + "JOIN users u ON u.id=om.user_id AND u.status='ACTIVE' "
            + "JOIN member_roles mr ON mr.organization_member_id=om.id "
            + "JOIN roles r ON r.id=mr.role_id "
            + "WHERE rp.organization_id=#{organizationId} AND rp.requirement_id=#{requirementId} "
            + "AND rp.role_code=#{roleCode} AND om.status='ACTIVE' AND r.code=CONCAT(#{roleCode},'_ADMIN') "
            + "UNION "
            + "SELECT DISTINCT re.user_id FROM requirement_executors re "
            + "JOIN organization_members om ON om.user_id=re.user_id AND om.organization_id=#{organizationId} "
            + "JOIN users u ON u.id=om.user_id AND u.status='ACTIVE' "
            + "JOIN member_roles mr ON mr.organization_member_id=om.id "
            + "JOIN roles r ON r.id=mr.role_id "
            + "WHERE re.organization_id=#{organizationId} AND re.requirement_id=#{requirementId} "
            + "AND re.role_code=#{roleCode} AND om.status='ACTIVE' "
            + "AND r.code IN (#{roleCode},CONCAT(#{roleCode},'_ADMIN'))"
            + ") members ORDER BY user_id")
    List<Long> requirementMembers(@Param("organizationId") long organizationId,
            @Param("requirementId") long requirementId, @Param("roleCode") String roleCode);
}
