package ai.forge.server.workitem.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkItemCollaborationMapper {

    @Select("SELECT COUNT(*) FROM work_items WHERE id = #{workItemId} AND workspace_id = #{workspaceId} "
            + "AND project_id = #{projectId} AND deleted_at IS NULL")
    int countScopedItem(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId);

    @Select("SELECT COUNT(*) FROM work_item_relations WHERE workspace_id = #{workspaceId} "
            + "AND project_id = #{projectId} AND source_id = #{sourceId} AND target_id = #{targetId} "
            + "AND relation_type = #{relationType}")
    int countRelation(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("sourceId") long sourceId,
            @Param("targetId") long targetId,
            @Param("relationType") String relationType);

    @Insert("INSERT INTO work_item_relations (workspace_id, project_id, source_id, target_id, relation_type, "
            + "created_by, created_at) VALUES (#{workspaceId}, #{projectId}, #{sourceId}, #{targetId}, "
            + "#{relationType}, #{createdBy}, UTC_TIMESTAMP(6))")
    int insertRelation(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("sourceId") long sourceId,
            @Param("targetId") long targetId,
            @Param("relationType") String relationType,
            @Param("createdBy") long createdBy);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Insert("INSERT INTO work_item_labels (workspace_id, project_id, work_item_id, label, created_by, created_at) "
            + "VALUES (#{workspaceId}, #{projectId}, #{workItemId}, #{label}, #{createdBy}, UTC_TIMESTAMP(6))")
    int insertLabel(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId,
            @Param("label") String label,
            @Param("createdBy") long createdBy);

    @Select("SELECT id, source_id, target_id, relation_type, created_by, created_at "
            + "FROM work_item_relations WHERE workspace_id = #{workspaceId} AND project_id = #{projectId} "
            + "AND (source_id = #{workItemId} OR target_id = #{workItemId}) ORDER BY id")
    List<Map<String, Object>> findRelations(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId);

    @Insert("INSERT INTO comments (workspace_id, project_id, work_item_id, author_user_id, body, created_at, "
            + "updated_at, deleted_at, version) VALUES (#{workspaceId}, #{projectId}, #{workItemId}, "
            + "#{authorId}, #{body}, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL, 0)")
    int insertComment(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId,
            @Param("authorId") long authorId,
            @Param("body") String body);

    @Select("SELECT id, actor_id, event_type, reason, NULL AS body, created_at, 'EVENT' AS kind "
            + "FROM work_item_events WHERE workspace_id = #{workspaceId} AND project_id = #{projectId} "
            + "AND work_item_id = #{workItemId} UNION ALL "
            + "SELECT id, author_user_id AS actor_id, NULL AS event_type, NULL AS reason, body, created_at, "
            + "'COMMENT' AS kind FROM comments WHERE workspace_id = #{workspaceId} AND project_id = #{projectId} "
            + "AND work_item_id = #{workItemId} AND deleted_at IS NULL ORDER BY created_at, kind, id")
    List<Map<String, Object>> findActivity(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId);
}
