package ai.forge.server.workitem.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RequirementWorkflowMapper {

    @Select("SELECT goal, in_scope, JSON_LENGTH(acceptance_criteria_json) AS acceptance_count "
            + "FROM requirement_details d JOIN work_items w ON w.id = d.work_item_id "
            + "WHERE d.work_item_id = #{workItemId} AND d.workspace_id = #{workspaceId} "
            + "AND w.workspace_id = #{workspaceId} AND w.project_id = #{projectId} "
            + "AND w.type = 'REQUIREMENT' AND w.deleted_at IS NULL")
    List<Map<String, Object>> findMaterial(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId);

    @Select("SELECT COUNT(*) FROM documents WHERE workspace_id = #{workspaceId} AND project_id = #{projectId} "
            + "AND work_item_id = #{workItemId} AND type = 'PRD' AND status = 'PUBLISHED' AND deleted_at IS NULL")
    int countPublishedPrd(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId);

    @Select("SELECT id, event_type, to_status, metadata_json FROM work_item_events "
            + "WHERE workspace_id = #{workspaceId} AND project_id = #{projectId} "
            + "AND work_item_id = #{workItemId} AND idempotency_key = #{idempotencyKey}")
    List<Map<String, Object>> findIdempotentEvent(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId,
            @Param("idempotencyKey") String idempotencyKey);

    @Update("UPDATE work_items SET status = #{toStatus}, version = version + 1, updated_at = UTC_TIMESTAMP(6) "
            + "WHERE id = #{workItemId} AND workspace_id = #{workspaceId} AND project_id = #{projectId} "
            + "AND type = 'REQUIREMENT' AND status = #{fromStatus} AND version = #{expectedVersion} "
            + "AND deleted_at IS NULL")
    int updateStatus(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("expectedVersion") long expectedVersion);

    @Insert("INSERT INTO review_records (workspace_id, project_id, work_item_id, review_type, status, "
            + "reviewer_user_id, comment, checklist_json, artifact_version_json, created_at) VALUES "
            + "(#{workspaceId}, #{projectId}, #{workItemId}, 'PRODUCT_REVIEW', #{reviewStatus}, "
            + "#{reviewerUserId}, #{comment}, JSON_OBJECT(), JSON_OBJECT(), UTC_TIMESTAMP(6))")
    int insertReview(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId,
            @Param("reviewStatus") String reviewStatus,
            @Param("reviewerUserId") Long reviewerUserId,
            @Param("comment") String comment);

    @Insert("INSERT INTO work_item_events (workspace_id, project_id, work_item_id, event_type, from_status, "
            + "to_status, actor_type, actor_id, reason, metadata_json, idempotency_key, created_at) VALUES "
            + "(#{workspaceId}, #{projectId}, #{workItemId}, #{eventType}, #{fromStatus}, #{toStatus}, "
            + "'USER', #{actorId}, #{reason}, JSON_OBJECT('version', #{version}), #{idempotencyKey}, UTC_TIMESTAMP(6))")
    int insertEvent(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId,
            @Param("eventType") String eventType,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("actorId") long actorId,
            @Param("reason") String reason,
            @Param("version") long version,
            @Param("idempotencyKey") String idempotencyKey);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Select("SELECT id, workspace_id, project_id, work_item_id, event_type, from_status, to_status, actor_id, "
            + "reason, idempotency_key, created_at FROM work_item_events WHERE workspace_id = #{workspaceId} "
            + "AND project_id = #{projectId} AND work_item_id = #{workItemId} ORDER BY id")
    List<Map<String, Object>> findEvents(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId);
}
