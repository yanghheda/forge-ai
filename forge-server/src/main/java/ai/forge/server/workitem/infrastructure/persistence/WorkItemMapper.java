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

    @Select("SELECT next_value FROM organization_item_sequences "
            + "WHERE organization_id = #{organizationId} FOR UPDATE")
    List<Map<String, Object>> lockSequence(
            @Param("organizationId") long organizationId);

    @Update("UPDATE organization_item_sequences SET next_value = next_value + 1, version = version + 1 "
            + "WHERE organization_id = #{organizationId} AND next_value = #{allocatedValue}")
    int consumeSequence(
            @Param("organizationId") long organizationId, @Param("allocatedValue") long allocatedValue);

    @Insert("INSERT INTO work_items (organization_id, item_number, item_key, type, title, description, "
            + "status, priority, parent_id, assignee_user_id, reporter_user_id, due_at, severity, blocked_at, "
            + "blocked_reason, blocked_by, created_at, updated_at, deleted_at, version) VALUES "
            + "(#{organizationId}, #{itemNumber}, #{itemKey}, #{type}, #{title}, #{description}, "
            + "#{status}, #{priority}, #{parentId}, #{assigneeUserId}, #{reporterUserId}, #{dueAt}, NULL, NULL, NULL, "
            + "NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL, 0)")
    int insert(
            @Param("organizationId") long organizationId,
            @Param("itemNumber") long itemNumber,
            @Param("itemKey") String itemKey,
            @Param("type") String type,
            @Param("title") String title,
            @Param("description") String description,
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("parentId") Long parentId,
            @Param("assigneeUserId") Long assigneeUserId,
            @Param("reporterUserId") long reporterUserId,
            @Param("dueAt") Instant dueAt);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Select("SELECT id, organization_id, item_number, item_key, type, title, description, status, "
            + "priority, assignee_user_id, reporter_user_id, due_at, version, created_at, updated_at "
            + "FROM work_items WHERE id = #{workItemId} AND organization_id = #{organizationId} "
            + "AND deleted_at IS NULL")
    List<Map<String, Object>> findByIdAndScope(
            @Param("organizationId") long organizationId,
            @Param("workItemId") long workItemId);

    @Select("<script>SELECT id, organization_id, item_number, item_key, type, title, status, "
            + "priority, assignee_user_id, due_at, version, created_at, updated_at "
            + "FROM work_items WHERE organization_id = #{organizationId} "
            + "AND deleted_at IS NULL <if test='type != null'>AND type = #{type}</if> "
            + "<if test='status != null'>AND status = #{status}</if> "
            + "ORDER BY item_number DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Map<String, Object>> findPage(
            @Param("organizationId") long organizationId,
            @Param("type") String type,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) FROM work_items WHERE organization_id = #{organizationId} "
            + "AND deleted_at IS NULL "
            + "<if test='type != null'>AND type = #{type}</if> "
            + "<if test='status != null'>AND status = #{status}</if></script>")
    long countPage(
            @Param("organizationId") long organizationId,
            @Param("type") String type,
            @Param("status") String status);

    @Update("UPDATE work_items SET title = #{title}, description = #{description}, priority = #{priority}, "
            + "assignee_user_id = #{assigneeUserId}, due_at = #{dueAt}, updated_at = UTC_TIMESTAMP(6), "
            + "version = version + 1 WHERE id = #{workItemId} AND organization_id = #{organizationId} "
            + "AND deleted_at IS NULL AND version = #{expectedVersion}")
    int update(
            @Param("organizationId") long organizationId,
            @Param("workItemId") long workItemId,
            @Param("title") String title,
            @Param("description") String description,
            @Param("priority") String priority,
            @Param("assigneeUserId") Long assigneeUserId,
            @Param("dueAt") Instant dueAt,
            @Param("expectedVersion") long expectedVersion);
}
