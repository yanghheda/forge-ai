package ai.forge.server.project.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProjectMapper {
    @Insert("INSERT INTO projects (workspace_id, `key`, name, description, status, created_by, created_at, updated_at, archived_at, version) VALUES (#{workspaceId}, #{key}, #{name}, #{description}, 'ACTIVE', #{creatorUserId}, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL, 0)")
    int insertProject(@Param("workspaceId") long workspaceId, @Param("creatorUserId") long creatorUserId, @Param("key") String key, @Param("name") String name, @Param("description") String description);
    @Select("SELECT LAST_INSERT_ID()") long lastInsertId();
    @Insert("INSERT INTO project_members (workspace_id, project_id, user_id, status, created_at, updated_at) VALUES (#{workspaceId}, #{projectId}, #{userId}, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))")
    int insertCreatorMember(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId, @Param("userId") long userId);
    @Select("SELECT p.id, p.workspace_id, p.`key` AS `key`, p.name, p.description, p.status, p.archived_at, p.version, p.created_at, p.updated_at FROM projects p WHERE p.workspace_id = #{workspaceId} ORDER BY p.id")
    List<Map<String,Object>> findByWorkspaceId(@Param("workspaceId") long workspaceId);
    @Select("SELECT p.id, p.workspace_id, p.`key` AS `key`, p.name, p.description, p.status, p.archived_at, p.version, p.created_at, p.updated_at FROM projects p WHERE p.id = #{projectId} AND p.workspace_id = #{workspaceId}")
    List<Map<String,Object>> findByIdAndWorkspaceId(@Param("projectId") long projectId, @Param("workspaceId") long workspaceId);
    @Select("SELECT EXISTS(SELECT 1 FROM project_members pm JOIN workspace_members wm ON wm.workspace_id = pm.workspace_id AND wm.user_id = pm.user_id WHERE pm.workspace_id = #{workspaceId} AND pm.project_id = #{projectId} AND pm.user_id = #{userId} AND pm.status = 'ACTIVE' AND wm.status = 'ACTIVE')")
    boolean hasActiveMember(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId, @Param("userId") long userId);
    @Update("UPDATE projects SET status = 'ARCHIVED', archived_at = UTC_TIMESTAMP(6), updated_at = UTC_TIMESTAMP(6), version = version + 1 WHERE id = #{projectId} AND workspace_id = #{workspaceId} AND status = 'ACTIVE' AND version = #{expectedVersion}")
    int archive(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId, @Param("expectedVersion") long expectedVersion);
    @Select("SELECT pm.id, pm.workspace_id, pm.project_id, u.id AS user_id, u.email, u.display_name, pm.status FROM project_members pm JOIN users u ON u.id = pm.user_id WHERE pm.project_id = #{projectId} AND pm.workspace_id = #{workspaceId} ORDER BY u.id")
    List<Map<String,Object>> findMembers(@Param("projectId") long projectId, @Param("workspaceId") long workspaceId);
    @Insert("INSERT INTO project_members (workspace_id, project_id, user_id, status, created_at, updated_at) VALUES (#{workspaceId}, #{projectId}, #{userId}, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE status = 'ACTIVE', updated_at = UTC_TIMESTAMP(6)")
    int activateMember(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId, @Param("userId") long userId);
    @Update("UPDATE project_members SET status = 'REMOVED', updated_at = UTC_TIMESTAMP(6) WHERE workspace_id = #{workspaceId} AND project_id = #{projectId} AND user_id = #{userId} AND status = 'ACTIVE'")
    int removeMember(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId, @Param("userId") long userId);
}
