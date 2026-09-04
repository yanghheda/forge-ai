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

    /* 与 hasProjectPermission 相同的范围门与权限合并语义，一次性列出全部可访问项目。 */
    @Select("SELECT DISTINCT p.id FROM projects p "
            + "WHERE p.workspace_id = #{workspaceId} "
            + "AND EXISTS (SELECT 1 FROM workspace_members wm0 WHERE wm0.workspace_id = #{workspaceId} AND wm0.user_id = #{userId} AND wm0.status = 'ACTIVE') "
            + "AND (EXISTS (SELECT 1 FROM workspace_members wm JOIN member_roles mr ON mr.workspace_member_id = wm.id JOIN roles r ON r.id = mr.role_id "
            + "WHERE wm.workspace_id = #{workspaceId} AND wm.user_id = #{userId} AND wm.status = 'ACTIVE' AND mr.project_id IS NULL AND r.code IN ('OWNER','ADMIN')) "
            + "OR EXISTS (SELECT 1 FROM project_members pm WHERE pm.workspace_id = #{workspaceId} AND pm.project_id = p.id AND pm.user_id = #{userId} AND pm.status = 'ACTIVE')) "
            + "AND EXISTS (SELECT 1 FROM workspace_members wm2 JOIN member_roles mr2 ON mr2.workspace_member_id = wm2.id "
            + "JOIN role_permissions rp ON rp.role_id = mr2.role_id JOIN permissions perm ON perm.id = rp.permission_id "
            + "WHERE wm2.workspace_id = #{workspaceId} AND wm2.user_id = #{userId} AND wm2.status = 'ACTIVE' "
            + "AND (mr2.project_id IS NULL OR mr2.project_id = p.id) AND perm.code = #{permission}) "
            + "ORDER BY p.id")
    List<Long> projectIdsWithPermission(@Param("userId") long userId, @Param("workspaceId") long workspaceId,
            @Param("permission") String permission);
}
