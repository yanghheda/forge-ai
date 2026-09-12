package ai.forge.server.workitem.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrganizationRequirementMapper {

    @Select("<script>SELECT wi.id, wi.item_key, wi.title, wi.description, wi.status, wi.priority, "
            + "o.name AS organization_name, reporter.display_name AS reporter_name, wi.due_at, "
            + "wi.version, wi.created_at, wi.updated_at FROM work_items wi "
            + "JOIN organizations o ON o.id = wi.organization_id "
            + "JOIN users reporter ON reporter.id = wi.reporter_user_id "
            + "WHERE wi.organization_id = #{organizationId} "
            + "AND wi.type = 'REQUIREMENT' AND wi.deleted_at IS NULL "
            + "<if test='participantUserId != null'>AND EXISTS (SELECT 1 FROM requirement_participants rp "
            + "WHERE rp.requirement_id = wi.id AND rp.organization_id = #{organizationId} "
            + "AND rp.user_id = #{participantUserId})</if> "
            + "<if test='query != null and query != &quot;&quot;'>AND (wi.title LIKE CONCAT('%', #{query}, '%') "
            + "OR wi.item_key LIKE CONCAT('%', #{query}, '%') OR wi.description LIKE CONCAT('%', #{query}, '%'))</if> "
            + "<if test='status != null'>AND wi.status = #{status}</if> "
            + "ORDER BY wi.item_number DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Map<String, Object>> findRequirements(
            @Param("organizationId") long organizationId,
            @Param("participantUserId") Long participantUserId,
            @Param("query") String query,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("SELECT wi.id, wi.item_key, wi.title, wi.description, wi.status, wi.priority, "
            + "o.name AS organization_name, reporter.display_name AS reporter_name, wi.due_at, "
            + "rd.goal, rd.in_scope, rd.out_of_scope, rd.acceptance_criteria_json, rd.business_value, "
            + "wi.version, wi.created_at, wi.updated_at FROM work_items wi "
            + "JOIN organizations o ON o.id = wi.organization_id "
            + "JOIN users reporter ON reporter.id = wi.reporter_user_id "
            + "LEFT JOIN requirement_details rd ON rd.work_item_id = wi.id AND rd.organization_id = #{organizationId} "
            + "WHERE wi.organization_id = #{organizationId} AND wi.id = #{requirementId} "
            + "AND wi.type = 'REQUIREMENT' AND wi.deleted_at IS NULL")
    List<Map<String, Object>> findRequirement(
            @Param("organizationId") long organizationId,
            @Param("requirementId") long requirementId);

    @Select("<script>SELECT COUNT(*) FROM work_items wi "
            + "WHERE wi.organization_id = #{organizationId} "
            + "AND wi.type = 'REQUIREMENT' AND wi.deleted_at IS NULL "
            + "<if test='participantUserId != null'>AND EXISTS (SELECT 1 FROM requirement_participants rp "
            + "WHERE rp.requirement_id = wi.id AND rp.organization_id = #{organizationId} "
            + "AND rp.user_id = #{participantUserId})</if> "
            + "<if test='query != null and query != &quot;&quot;'>AND (wi.title LIKE CONCAT('%', #{query}, '%') "
            + "OR wi.item_key LIKE CONCAT('%', #{query}, '%') OR wi.description LIKE CONCAT('%', #{query}, '%'))</if> "
            + "<if test='status != null'>AND wi.status = #{status}</if></script>")
    long countRequirements(
            @Param("organizationId") long organizationId,
            @Param("participantUserId") Long participantUserId,
            @Param("query") String query,
            @Param("status") String status);

    @Select("SELECT COUNT(*) AS total, "
            + "SUM(CASE WHEN status NOT IN ('DONE', 'RELEASED') THEN 1 ELSE 0 END) AS in_progress, "
            + "SUM(CASE WHEN status IN ('DONE', 'RELEASED') THEN 1 ELSE 0 END) AS completed "
            + "FROM work_items WHERE organization_id = #{organizationId} "
            + "AND type = 'REQUIREMENT' AND deleted_at IS NULL")
    Map<String, Object> overview(@Param("organizationId") long organizationId);

    @Select("SELECT rp.role_code, u.id AS user_id, u.display_name, u.email "
            + "FROM requirement_participants rp JOIN users u ON u.id = rp.user_id "
            + "WHERE rp.organization_id = #{organizationId} "
            + "AND rp.requirement_id = #{requirementId} ORDER BY FIELD(rp.role_code, 'PRODUCT','UX','DEVELOPER','QA')")
    List<Map<String, Object>> findParticipants(
            @Param("organizationId") long organizationId,
            @Param("requirementId") long requirementId);

    @Select("SELECT EXISTS(SELECT 1 FROM organization_members om "
            + "JOIN member_roles mr ON mr.organization_member_id = om.id "
            + "JOIN roles r ON r.id = mr.role_id "
            + "WHERE om.organization_id = #{organizationId} AND om.user_id = #{userId} AND om.status = 'ACTIVE' "
            + "AND r.code IN (#{role}, 'OWNER'))")
    boolean memberCanFillRole(
            @Param("organizationId") long organizationId,
            @Param("userId") long userId,
            @Param("role") String role);

    @Select("SELECT EXISTS(SELECT 1 FROM organization_members om "
            + "JOIN member_roles mr ON mr.organization_member_id = om.id "
            + "JOIN roles r ON r.id = mr.role_id "
            + "WHERE om.organization_id = #{organizationId} AND om.user_id = #{userId} AND om.status = 'ACTIVE' "
            + "AND r.code = #{role})")
    boolean memberHasRole(
            @Param("organizationId") long organizationId,
            @Param("userId") long userId,
            @Param("role") String role);

    @Delete("DELETE FROM requirement_participants WHERE organization_id = #{organizationId} "
            + "AND requirement_id = #{requirementId}")
    int deleteParticipants(
            @Param("organizationId") long organizationId,
            @Param("requirementId") long requirementId);

    @Insert("INSERT INTO requirement_participants "
            + "(organization_id, requirement_id, role_code, user_id, assigned_by, created_at, updated_at) "
            + "VALUES (#{organizationId}, #{requirementId}, #{role}, #{userId}, #{actorUserId}, "
            + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))")
    int insertParticipant(
            @Param("organizationId") long organizationId,
            @Param("requirementId") long requirementId,
            @Param("role") String role,
            @Param("userId") long userId,
            @Param("actorUserId") long actorUserId);

    @Select("SELECT u.id AS user_id, u.display_name, u.email, GROUP_CONCAT(DISTINCT r.code ORDER BY r.code) AS roles "
            + "FROM organization_members om JOIN users u ON u.id = om.user_id AND u.status = 'ACTIVE' "
            + "JOIN member_roles mr ON mr.organization_member_id = om.id "
            + "JOIN roles r ON r.id = mr.role_id "
            + "WHERE om.organization_id = #{organizationId} AND om.status = 'ACTIVE' "
            + "GROUP BY u.id, u.display_name, u.email ORDER BY u.display_name, u.id")
    List<Map<String, Object>> findMembers(
            @Param("organizationId") long organizationId);
}
