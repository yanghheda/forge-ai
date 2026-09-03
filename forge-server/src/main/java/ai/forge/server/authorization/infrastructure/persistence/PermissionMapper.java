package ai.forge.server.authorization.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PermissionMapper {

    @Select("SELECT EXISTS(SELECT 1 FROM projects WHERE id = #{projectId} AND workspace_id = #{workspaceId})")
    boolean projectExists(@Param("projectId") long projectId, @Param("workspaceId") long workspaceId);

    @Select("SELECT EXISTS(SELECT 1 FROM workspace_members WHERE workspace_id = #{workspaceId} AND user_id = #{userId} AND status = 'ACTIVE')")
    boolean activeWorkspaceMember(@Param("userId") long userId, @Param("workspaceId") long workspaceId);

    @Select("SELECT EXISTS(SELECT 1 FROM project_members WHERE workspace_id = #{workspaceId} AND project_id = #{projectId} AND user_id = #{userId} AND status = 'ACTIVE')")
    boolean activeProjectMember(@Param("userId") long userId, @Param("workspaceId") long workspaceId, @Param("projectId") long projectId);

    @Select("SELECT DISTINCT p.code FROM workspace_members wm JOIN member_roles mr ON mr.workspace_member_id = wm.id JOIN role_permissions rp ON rp.role_id = mr.role_id JOIN permissions p ON p.id = rp.permission_id WHERE wm.workspace_id = #{workspaceId} AND wm.user_id = #{userId} AND wm.status = 'ACTIVE' AND mr.project_id IS NULL")
    List<String> workspacePermissions(@Param("userId") long userId, @Param("workspaceId") long workspaceId);

    @Select("SELECT DISTINCT p.code FROM workspace_members wm JOIN member_roles mr ON mr.workspace_member_id = wm.id JOIN role_permissions rp ON rp.role_id = mr.role_id JOIN permissions p ON p.id = rp.permission_id WHERE wm.workspace_id = #{workspaceId} AND wm.user_id = #{userId} AND wm.status = 'ACTIVE' AND (mr.project_id IS NULL OR mr.project_id = #{projectId})")
    List<String> projectPermissions(@Param("userId") long userId, @Param("workspaceId") long workspaceId, @Param("projectId") long projectId);

    @Select("SELECT DISTINCT r.code FROM workspace_members wm JOIN member_roles mr ON mr.workspace_member_id = wm.id JOIN roles r ON r.id = mr.role_id WHERE wm.workspace_id = #{workspaceId} AND wm.user_id = #{userId} AND wm.status = 'ACTIVE' AND (mr.project_id IS NULL OR mr.project_id = #{projectId})")
    List<String> projectRoles(@Param("userId") long userId, @Param("workspaceId") long workspaceId, @Param("projectId") long projectId);
}
