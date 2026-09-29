package ai.forge.server.console.infrastructure.persistence;

import ai.forge.server.console.application.ConsoleStore;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    @Select("SELECT wi.id,wi.item_key,wi.type,wi.title,wi.assignee_user_id,wi.parent_id requirement_id,"
            + "wi.status lane,COALESCE(bp.position,wi.id*1000) position,wi.version,"
            + "wi.priority,wi.due_at,wi.blocked_reason,assignee.display_name assignee_name "
            + "FROM work_items wi "
            + "LEFT JOIN board_positions bp ON bp.work_item_id=wi.id AND bp.organization_id=#{organizationId} "
            + "LEFT JOIN users assignee ON assignee.id=wi.assignee_user_id "
            + "WHERE wi.organization_id=#{organizationId} AND wi.deleted_at IS NULL AND wi.type IN ('UX_TASK','DEV_TASK','QA_TASK','BUG') ORDER BY lane,position,wi.id")
    List<Map<String, Object>> board(@Param("organizationId") long organizationId);

    @Select("<script>SELECT wi.id, wi.item_key, wi.title FROM work_items wi "
            + "WHERE wi.organization_id=#{organizationId} AND wi.type='REQUIREMENT' AND wi.deleted_at IS NULL "
            + "<if test='userId != null'>AND (EXISTS(SELECT 1 FROM requirement_participants rp "
            + "WHERE rp.requirement_id=wi.id AND rp.organization_id=#{organizationId} AND rp.user_id=#{userId}) "
            + "OR EXISTS(SELECT 1 FROM requirement_executors re WHERE re.organization_id=#{organizationId} "
            + "AND re.requirement_id=wi.id AND re.user_id=#{userId}))</if> "
            + "ORDER BY wi.item_number DESC LIMIT 200</script>")
    List<Map<String, Object>> requirementsByParticipant(@Param("organizationId") long organizationId,
            @Param("userId") Long userId);

    /*
     * 看板创建入口的数据源：当前用户是本阶段负责人、需求处于对应准备态、且该领域已关联有效执行人。
     * 负责人与执行人语义必须与 DeliveryWorkflowMapper.isLeader/isExecutor 一致：成员与用户必须 ACTIVE，
     * 负责人要求角色码为 role_ADMIN，执行人要求角色码为 role 或 role_ADMIN。
     */
    @Select("SELECT wi.id requirement_id,wi.item_key requirement_item_key,wi.title requirement_title,"
            + "wi.status requirement_status,rp.role_code,executor.user_id executor_user_id,executor.display_name executor_display_name "
            + "FROM work_items wi "
            + "JOIN requirement_participants rp ON rp.organization_id=wi.organization_id AND rp.requirement_id=wi.id AND rp.user_id=#{userId} "
            + "LEFT JOIN (SELECT DISTINCT re.requirement_id,re.role_code,re.user_id,u.display_name FROM requirement_executors re "
            + "JOIN organization_members om ON om.organization_id=re.organization_id AND om.user_id=re.user_id AND om.status='ACTIVE' "
            + "JOIN users u ON u.id=om.user_id AND u.status='ACTIVE' "
            + "JOIN member_roles mr ON mr.organization_member_id=om.id "
            + "JOIN roles r ON r.id=mr.role_id AND r.code IN (re.role_code,CONCAT(re.role_code,'_ADMIN')) "
            + "WHERE re.organization_id=#{organizationId}) executor "
            + "ON executor.requirement_id=wi.id AND executor.role_code=rp.role_code "
            + "WHERE wi.organization_id=#{organizationId} AND wi.type='REQUIREMENT' AND wi.deleted_at IS NULL "
            + "AND ((wi.status='READY_FOR_UX' AND rp.role_code='UX') OR (wi.status='READY_FOR_DEV' AND rp.role_code='DEVELOPER') "
            + "OR (wi.status='READY_FOR_QA' AND rp.role_code='QA')) "
            + "AND EXISTS(SELECT 1 FROM organization_members leader_om "
            + "JOIN users leader_user ON leader_user.id=leader_om.user_id AND leader_user.status='ACTIVE' "
            + "JOIN member_roles leader_mr ON leader_mr.organization_member_id=leader_om.id "
            + "JOIN roles leader_role ON leader_role.id=leader_mr.role_id AND leader_role.code=CONCAT(rp.role_code,'_ADMIN') "
            + "WHERE leader_om.organization_id=wi.organization_id AND leader_om.user_id=rp.user_id AND leader_om.status='ACTIVE') "
            + "ORDER BY wi.item_number DESC,executor.user_id")
    List<Map<String, Object>> taskCreationOptions(@Param("organizationId") long organizationId,
            @Param("userId") long userId);

    @Select("SELECT COUNT(*) FROM work_items WHERE id=#{workItemId} AND organization_id=#{organizationId} AND deleted_at IS NULL")
    int workItemExists(@Param("organizationId") long organizationId, @Param("workItemId") long workItemId);

    @Insert("INSERT INTO board_positions(work_item_id,organization_id,lane,position,updated_by,updated_at,version) "
            + "VALUES(#{workItemId},#{organizationId},#{lane},#{position},#{userId},UTC_TIMESTAMP(6),0) "
            + "ON DUPLICATE KEY UPDATE lane=VALUES(lane),position=VALUES(position),updated_by=VALUES(updated_by),"
            + "updated_at=UTC_TIMESTAMP(6),version=version+1")
    int savePosition(@Param("organizationId") long organizationId, @Param("userId") long userId,
            @Param("workItemId") long workItemId, @Param("lane") String lane, @Param("position") long position);
}
