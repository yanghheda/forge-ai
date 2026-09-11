package ai.forge.server.console.infrastructure.persistence;

import ai.forge.server.console.application.ConsoleStore;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConsoleMapper extends ConsoleStore {
    @Select("(SELECT 'REQUIREMENT' type, CAST(id AS CHAR) id, title, item_key subtitle FROM work_items "
            + "WHERE organization_id=#{organizationId} AND type='REQUIREMENT' AND deleted_at IS NULL "
            + "AND (title LIKE CONCAT('%',#{query},'%') OR item_key LIKE CONCAT('%',#{query},'%')) LIMIT 8) UNION ALL "
            + "(SELECT 'DOCUMENT', CAST(id AS CHAR), title, type FROM documents WHERE organization_id=#{organizationId} "
            + "AND deleted_at IS NULL AND title LIKE CONCAT('%',#{query},'%') LIMIT 8) UNION ALL "
            + "(SELECT 'MEMBER', CAST(u.id AS CHAR), u.display_name, u.email FROM organization_members om "
            + "JOIN users u ON u.id=om.user_id WHERE om.organization_id=#{organizationId} AND om.status='ACTIVE' "
            + "AND (u.display_name LIKE CONCAT('%',#{query},'%') OR u.email LIKE CONCAT('%',#{query},'%')) LIMIT 8)")
    List<Map<String, Object>> search(@Param("organizationId") long organizationId, @Param("query") String query);

    @Select("SELECT id,type,title,body,resource_type,resource_id,read_at,created_at FROM notifications "
            + "WHERE organization_id=#{organizationId} AND user_id=#{userId} ORDER BY created_at DESC,id DESC LIMIT #{limit}")
    List<Map<String, Object>> notifications(@Param("organizationId") long organizationId,
            @Param("userId") long userId, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM notifications WHERE organization_id=#{organizationId} AND user_id=#{userId} AND read_at IS NULL")
    long unread(@Param("organizationId") long organizationId, @Param("userId") long userId);

    @Update("UPDATE notifications SET read_at=COALESCE(read_at,UTC_TIMESTAMP(6)) WHERE id=#{id} "
            + "AND organization_id=#{organizationId} AND user_id=#{userId}")
    int markRead(@Param("organizationId") long organizationId, @Param("userId") long userId, @Param("id") long id);

    @Select("SELECT COUNT(*) FROM work_items WHERE organization_id=#{organizationId} AND type='REQUIREMENT' "
            + "AND status IN ('DONE','RELEASED') AND updated_at>=UTC_TIMESTAMP(6)-INTERVAL 7 DAY AND deleted_at IS NULL")
    long weeklyDeliveries(@Param("organizationId") long organizationId);

    @Select("SELECT COUNT(DISTINCT skill) FROM agent_runs WHERE organization_id=#{organizationId} "
            + "AND created_at>=UTC_TIMESTAMP(6)-INTERVAL 7 DAY")
    long activeAgents(@Param("organizationId") long organizationId);

    @Select("SELECT COUNT(*) FROM work_items WHERE organization_id=#{organizationId} AND assignee_user_id=#{userId} "
            + "AND status NOT IN ('DONE','RELEASED','CANCELLED') AND deleted_at IS NULL")
    long personalTodos(@Param("organizationId") long organizationId, @Param("userId") long userId);

    @Select("SELECT status,COUNT(*) total FROM work_items WHERE organization_id=#{organizationId} "
            + "AND type='REQUIREMENT' AND deleted_at IS NULL GROUP BY status")
    List<Map<String, Object>> stages(@Param("organizationId") long organizationId);

    @Select("SELECT DATE(updated_at) day,COUNT(*) total FROM work_items WHERE organization_id=#{organizationId} "
            + "AND type='REQUIREMENT' AND status IN ('DONE','RELEASED') AND updated_at>=UTC_DATE()-INTERVAL 6 DAY "
            + "AND deleted_at IS NULL GROUP BY DATE(updated_at) ORDER BY day")
    List<Map<String, Object>> trend(@Param("organizationId") long organizationId);

    @Select("SELECT wi.id,wi.item_key,wi.type,wi.title,wi.assignee_user_id,"
            + "COALESCE(bp.lane,CASE WHEN wi.status IN ('DONE','RELEASED') THEN 'DONE' "
            + "WHEN wi.status='BLOCKED' THEN 'BLOCKED' WHEN wi.status IN ('TODO','DRAFT','OPEN') THEN 'TODO' "
            + "ELSE 'IN_PROGRESS' END) lane,COALESCE(bp.position,wi.id*1000) position,COALESCE(bp.version,0) version "
            + "FROM work_items wi LEFT JOIN board_positions bp ON bp.work_item_id=wi.id "
            + "WHERE wi.organization_id=#{organizationId} AND wi.deleted_at IS NULL ORDER BY lane,position,wi.id")
    List<Map<String, Object>> board(@Param("organizationId") long organizationId);

    @Select("SELECT COUNT(*) FROM work_items WHERE id=#{workItemId} AND organization_id=#{organizationId} AND deleted_at IS NULL")
    int workItemExists(@Param("organizationId") long organizationId, @Param("workItemId") long workItemId);

    @Insert("INSERT INTO board_positions(work_item_id,organization_id,lane,position,updated_by,updated_at,version) "
            + "VALUES(#{workItemId},#{organizationId},#{lane},#{position},#{userId},UTC_TIMESTAMP(6),0) "
            + "ON DUPLICATE KEY UPDATE lane=VALUES(lane),position=VALUES(position),updated_by=VALUES(updated_by),"
            + "updated_at=UTC_TIMESTAMP(6),version=version+1")
    int savePosition(@Param("organizationId") long organizationId, @Param("userId") long userId,
            @Param("workItemId") long workItemId, @Param("lane") String lane, @Param("position") long position);
}
