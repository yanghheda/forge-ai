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
            + "wi.version, wi.created_at, wi.updated_at FROM work_items wi "
            + "WHERE wi.workspace_id = #{workspaceId} AND wi.project_id = #{projectId} "
            + "AND wi.type = 'REQUIREMENT' AND wi.deleted_at IS NULL "
            + "<if test='participantUserId != null'>AND EXISTS (SELECT 1 FROM requirement_participants rp "
            + "WHERE rp.requirement_id = wi.id AND rp.workspace_id = #{workspaceId} "
            + "AND rp.project_id = #{projectId} AND rp.user_id = #{participantUserId})</if> "
            + "<if test='query != null and query != &quot;&quot;'>AND (wi.title LIKE CONCAT('%', #{query}, '%') "
            + "OR wi.item_key LIKE CONCAT('%', #{query}, '%') OR wi.description LIKE CONCAT('%', #{query}, '%'))</if> "
            + "<if test='status != null'>AND wi.status = #{status}</if> "
            + "ORDER BY wi.item_number DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Map<String, Object>> findRequirements(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("participantUserId") Long participantUserId,
            @Param("query") String query,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) FROM work_items wi "
            + "WHERE wi.workspace_id = #{workspaceId} AND wi.project_id = #{projectId} "
            + "AND wi.type = 'REQUIREMENT' AND wi.deleted_at IS NULL "
            + "<if test='participantUserId != null'>AND EXISTS (SELECT 1 FROM requirement_participants rp "
            + "WHERE rp.requirement_id = wi.id AND rp.workspace_id = #{workspaceId} "
            + "AND rp.project_id = #{projectId} AND rp.user_id = #{participantUserId})</if> "
            + "<if test='query != null and query != &quot;&quot;'>AND (wi.title LIKE CONCAT('%', #{query}, '%') "
            + "OR wi.item_key LIKE CONCAT('%', #{query}, '%') OR wi.description LIKE CONCAT('%', #{query}, '%'))</if> "
            + "<if test='status != null'>AND wi.status = #{status}</if></script>")
    long countRequirements(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("participantUserId") Long participantUserId,
            @Param("query") String query,
            @Param("status") String status);

    @Select("SELECT COUNT(*) AS total, "
            + "SUM(CASE WHEN status NOT IN ('DONE', 'RELEASED') THEN 1 ELSE 0 END) AS in_progress, "
            + "SUM(CASE WHEN status IN ('DONE', 'RELEASED') THEN 1 ELSE 0 END) AS completed "
            + "FROM work_items WHERE workspace_id = #{workspaceId} AND project_id = #{projectId} "
            + "AND type = 'REQUIREMENT' AND deleted_at IS NULL")
    Map<String, Object> overview(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId);

    @Select("SELECT rp.role_code, u.id AS user_id, u.display_name, u.email "
            + "FROM requirement_participants rp JOIN users u ON u.id = rp.user_id "
            + "WHERE rp.workspace_id = #{workspaceId} AND rp.project_id = #{projectId} "
            + "AND rp.requirement_id = #{requirementId} ORDER BY FIELD(rp.role_code, 'PRODUCT','UX','DEVELOPER','QA')")
    List<Map<String, Object>> findParticipants(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("requirementId") long requirementId);

    @Select("SELECT EXISTS(SELECT 1 FROM workspace_members wm "
            + "JOIN project_members pm ON pm.workspace_id = wm.workspace_id AND pm.user_id = wm.user_id "
            + "AND pm.project_id = #{projectId} AND pm.status = 'ACTIVE' "
            + "JOIN member_roles mr ON mr.workspace_member_id = wm.id AND mr.project_id IS NULL "
            + "JOIN roles r ON r.id = mr.role_id "
            + "WHERE wm.workspace_id = #{workspaceId} AND wm.user_id = #{userId} AND wm.status = 'ACTIVE' "
            + "AND r.code IN (#{role}, 'OWNER'))")
    boolean memberCanFillRole(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("userId") long userId,
            @Param("role") String role);

    @Delete("DELETE FROM requirement_participants WHERE workspace_id = #{workspaceId} "
            + "AND project_id = #{projectId} AND requirement_id = #{requirementId}")
    int deleteParticipants(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("requirementId") long requirementId);

    @Insert("INSERT INTO requirement_participants "
            + "(workspace_id, project_id, requirement_id, role_code, user_id, assigned_by, created_at, updated_at) "
            + "VALUES (#{workspaceId}, #{projectId}, #{requirementId}, #{role}, #{userId}, #{actorUserId}, "
            + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))")
    int insertParticipant(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("requirementId") long requirementId,
            @Param("role") String role,
            @Param("userId") long userId,
            @Param("actorUserId") long actorUserId);

    @Select("SELECT u.id AS user_id, u.display_name, u.email, GROUP_CONCAT(DISTINCT r.code ORDER BY r.code) AS roles "
            + "FROM workspace_members wm JOIN users u ON u.id = wm.user_id AND u.status = 'ACTIVE' "
            + "LEFT JOIN project_members pm ON pm.workspace_id = wm.workspace_id AND pm.user_id = wm.user_id "
            + "AND pm.project_id = #{projectId} AND pm.status = 'ACTIVE' "
            + "JOIN member_roles mr ON mr.workspace_member_id = wm.id AND mr.project_id IS NULL "
            + "JOIN roles r ON r.id = mr.role_id "
            + "WHERE wm.workspace_id = #{workspaceId} AND wm.status = 'ACTIVE' AND (pm.id IS NOT NULL OR r.code = 'OWNER') "
            + "GROUP BY u.id, u.display_name, u.email ORDER BY u.display_name, u.id")
    List<Map<String, Object>> findMembers(
            @Param("workspaceId") long workspaceId, @Param("projectId") long projectId);
}
