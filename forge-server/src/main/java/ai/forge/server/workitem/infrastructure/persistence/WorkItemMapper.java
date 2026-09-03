package ai.forge.server.workitem.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WorkItemMapper {

    @Select("SELECT s.next_value, p.`key` AS project_key FROM project_item_sequences s "
            + "JOIN projects p ON p.id = s.project_id "
            + "WHERE s.project_id = #{projectId} AND p.workspace_id = #{workspaceId} AND p.status = 'ACTIVE' FOR UPDATE")
    List<Map<String, Object>> lockSequence(
            @Param("workspaceId") long workspaceId, @Param("projectId") long projectId);

    @Update("UPDATE project_item_sequences SET next_value = next_value + 1, version = version + 1 "
            + "WHERE project_id = #{projectId} AND next_value = #{allocatedValue}")
    int consumeSequence(
            @Param("projectId") long projectId, @Param("allocatedValue") long allocatedValue);

    @Insert("INSERT INTO work_items (workspace_id, project_id, item_number, item_key, type, title, description, "
            + "status, priority, parent_id, assignee_user_id, reporter_user_id, due_at, severity, blocked_at, "
            + "blocked_reason, blocked_by, created_at, updated_at, deleted_at, version) VALUES "
            + "(#{workspaceId}, #{projectId}, #{itemNumber}, #{itemKey}, #{type}, #{title}, #{description}, "
            + "#{status}, #{priority}, NULL, #{assigneeUserId}, #{reporterUserId}, #{dueAt}, NULL, NULL, NULL, "
            + "NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL, 0)")
    int insert(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("itemNumber") long itemNumber,
            @Param("itemKey") String itemKey,
            @Param("type") String type,
            @Param("title") String title,
            @Param("description") String description,
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("assigneeUserId") Long assigneeUserId,
            @Param("reporterUserId") long reporterUserId,
            @Param("dueAt") Instant dueAt);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Select("SELECT id, workspace_id, project_id, item_number, item_key, type, title, description, status, "
            + "priority, assignee_user_id, reporter_user_id, due_at, version, created_at, updated_at "
            + "FROM work_items WHERE id = #{workItemId} AND workspace_id = #{workspaceId} "
            + "AND project_id = #{projectId} AND deleted_at IS NULL")
    List<Map<String, Object>> findByIdAndScope(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId);

    @Select("<script>SELECT id, workspace_id, project_id, item_number, item_key, type, title, description, status, "
            + "priority, assignee_user_id, reporter_user_id, due_at, version, created_at, updated_at "
            + "FROM work_items WHERE workspace_id = #{workspaceId} AND project_id = #{projectId} "
            + "AND deleted_at IS NULL <if test='type != null'>AND type = #{type}</if> "
            + "<if test='status != null'>AND status = #{status}</if> "
            + "ORDER BY item_number DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Map<String, Object>> findPage(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("type") String type,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) FROM work_items WHERE workspace_id = #{workspaceId} "
            + "AND project_id = #{projectId} AND deleted_at IS NULL "
            + "<if test='type != null'>AND type = #{type}</if> "
            + "<if test='status != null'>AND status = #{status}</if></script>")
    long countPage(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("type") String type,
            @Param("status") String status);

    @Update("UPDATE work_items SET title = #{title}, description = #{description}, priority = #{priority}, "
            + "assignee_user_id = #{assigneeUserId}, due_at = #{dueAt}, updated_at = UTC_TIMESTAMP(6), "
            + "version = version + 1 WHERE id = #{workItemId} AND workspace_id = #{workspaceId} "
            + "AND project_id = #{projectId} AND deleted_at IS NULL AND version = #{expectedVersion}")
    int update(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId,
            @Param("title") String title,
            @Param("description") String description,
            @Param("priority") String priority,
            @Param("assigneeUserId") Long assigneeUserId,
            @Param("dueAt") Instant dueAt,
            @Param("expectedVersion") long expectedVersion);
}
