package ai.forge.server.workitem.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DeliveryGraphMapper {

    @Select("SELECT id, parent_id, type, item_key, title, status FROM work_items "
            + "WHERE workspace_id = #{workspaceId} AND project_id = #{projectId} AND deleted_at IS NULL ORDER BY id")
    List<Map<String, Object>> findItems(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId);

    @Select("SELECT id, source_id, target_id, relation_type FROM work_item_relations "
            + "WHERE workspace_id = #{workspaceId} AND project_id = #{projectId} ORDER BY id")
    List<Map<String, Object>> findRelations(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId);

    @Select("SELECT id, work_item_id, type, title, status FROM documents "
            + "WHERE workspace_id = #{workspaceId} AND project_id = #{projectId} "
            + "AND work_item_id IS NOT NULL AND deleted_at IS NULL ORDER BY id")
    List<Map<String, Object>> findDocuments(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId);
}
