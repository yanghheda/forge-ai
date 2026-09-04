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

    @Select("SELECT COUNT(*) FROM documents WHERE workspace_id = #{workspaceId} AND project_id = #{projectId} "
            + "AND work_item_id = #{workItemId} AND type = 'UX_SPEC' AND status = 'PUBLISHED' AND deleted_at IS NULL")
    int countPublishedUxSpec(
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
            + "AND type = #{type} AND status = #{fromStatus} AND version = #{expectedVersion} "
            + "AND deleted_at IS NULL")
    int updateStatus(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId,
            @Param("type") String type,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("expectedVersion") long expectedVersion);

    @Insert("INSERT INTO review_records (workspace_id, project_id, work_item_id, review_type, status, "
            + "reviewer_user_id, comment, checklist_json, artifact_version_json, created_at) VALUES "
            + "(#{workspaceId}, #{projectId}, #{workItemId}, #{reviewType}, #{reviewStatus}, "
            + "#{reviewerUserId}, #{comment}, CAST(#{checklist} AS JSON), CAST(#{artifactVersions} AS JSON), UTC_TIMESTAMP(6))")
    int insertReview(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId,
            @Param("reviewType") String reviewType,
            @Param("reviewStatus") String reviewStatus,
            @Param("reviewerUserId") Long reviewerUserId,
            @Param("comment") String comment,
            @Param("checklist") String checklist,
            @Param("artifactVersions") String artifactVersions);

    @Select("SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('documentId', d.id, 'versionId', d.current_version_id, "
            + "'type', d.type)), JSON_ARRAY()) FROM documents d WHERE d.workspace_id = #{workspaceId} "
            + "AND d.project_id = #{projectId} AND d.work_item_id = #{workItemId} "
            + "AND d.status = 'PUBLISHED' AND d.deleted_at IS NULL AND d.type IN ('PRD', 'UX_SPEC', 'PROTOTYPE_SPEC', 'DESIGN_GUIDE')")
    String publishedArtifactVersions(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId);

    @Select("SELECT COUNT(*) FROM work_items WHERE workspace_id = #{workspaceId} AND project_id = #{projectId} "
            + "AND parent_id = #{requirementId} AND type = 'UX_TASK' AND deleted_at IS NULL")
    int countUxTaskForRequirement(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("requirementId") long requirementId);

    @Select("SELECT next_value FROM project_item_sequences WHERE project_id = #{projectId} FOR UPDATE")
    long lockNextItemNumber(@Param("projectId") long projectId);

    @Update("UPDATE project_item_sequences SET next_value = next_value + 1, version = version + 1 WHERE project_id = #{projectId}")
    int advanceItemNumber(@Param("projectId") long projectId);

    @Select("SELECT `key` FROM projects WHERE id = #{projectId} AND workspace_id = #{workspaceId}")
    String projectKey(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId);

    @Select("SELECT title FROM work_items WHERE id = #{workItemId} AND workspace_id = #{workspaceId} "
            + "AND project_id = #{projectId} AND type = 'REQUIREMENT' AND deleted_at IS NULL")
    String requirementTitle(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId);

    @Insert("INSERT INTO work_items (workspace_id, project_id, item_number, item_key, type, title, description, status, "
            + "priority, parent_id, assignee_user_id, reporter_user_id, due_at, severity, blocked_at, blocked_reason, "
            + "blocked_by, created_at, updated_at, deleted_at, version) VALUES (#{workspaceId}, #{projectId}, #{number}, "
            + "#{itemKey}, 'UX_TASK', #{title}, '', 'TODO', 'MEDIUM', #{requirementId}, NULL, #{actorId}, NULL, NULL, "
            + "NULL, NULL, NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL, 0)")
    int insertUxTask(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("requirementId") long requirementId,
            @Param("actorId") long actorId,
            @Param("number") long number,
            @Param("itemKey") String itemKey,
            @Param("title") String title);

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
