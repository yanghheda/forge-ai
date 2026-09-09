package ai.forge.server.workspace.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WorkspaceMapper {
    @Select("SELECT w.id, w.organization_id, w.name, w.slug FROM workspaces w JOIN workspace_members wm ON wm.workspace_id = w.id WHERE wm.user_id = #{userId} AND wm.status = 'ACTIVE' AND w.status = 'ACTIVE' ORDER BY w.id")
    List<Map<String, Object>> findActiveByUserId(@Param("userId") long userId);
    @Select("SELECT w.id, w.organization_id, w.name, w.slug FROM workspaces w JOIN workspace_members wm ON wm.workspace_id = w.id WHERE w.id = #{workspaceId} AND wm.user_id = #{userId} AND wm.status = 'ACTIVE' AND w.status = 'ACTIVE'")
    List<Map<String, Object>> findActiveByIdAndUserId(@Param("workspaceId") long workspaceId, @Param("userId") long userId);
    @Select("SELECT w.id, w.organization_id, w.name, w.slug FROM workspaces w JOIN workspace_members wm ON wm.workspace_id = w.id WHERE w.slug = #{slug} AND wm.user_id = #{userId} AND wm.status = 'ACTIVE' AND w.status = 'ACTIVE'")
    List<Map<String, Object>> findActiveBySlugAndUserId(@Param("slug") String slug, @Param("userId") long userId);
    @Select("SELECT EXISTS(SELECT 1 FROM workspace_members wm JOIN member_roles mr ON mr.workspace_member_id = wm.id AND mr.project_id IS NULL JOIN roles r ON r.id = mr.role_id WHERE wm.workspace_id = #{workspaceId} AND wm.user_id = #{userId} AND wm.status = 'ACTIVE' AND r.code = 'OWNER' AND r.system_role = TRUE)")
    boolean isActiveOwner(@Param("workspaceId") long workspaceId, @Param("userId") long userId);
    @Select("SELECT EXISTS(SELECT 1 FROM workspace_members wm JOIN member_roles mr ON mr.workspace_member_id = wm.id AND mr.project_id IS NULL JOIN roles r ON r.id = mr.role_id WHERE wm.user_id = #{userId} AND wm.status = 'ACTIVE' AND r.code = 'OWNER' AND r.system_role = TRUE)")
    boolean hasAnyActiveOwnerRole(@Param("userId") long userId);
    @Select("SELECT default_organization_id FROM instance_settings WHERE id = 1 AND initialized_at IS NOT NULL")
    Long defaultOrganizationId();
    @Insert("INSERT INTO workspaces (organization_id, name, slug, status, settings_json, created_at, updated_at, version) VALUES (#{organizationId}, #{name}, #{slug}, 'ACTIVE', JSON_OBJECT(), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)")
    int insertWorkspace(@Param("organizationId") long organizationId, @Param("name") String name, @Param("slug") String slug);
    @Select("SELECT LAST_INSERT_ID()") long lastInsertId();
    @Insert("INSERT INTO workspace_members (workspace_id, user_id, status, joined_at, created_at, updated_at, version) VALUES (#{workspaceId}, #{userId}, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)")
    int insertMember(@Param("workspaceId") long workspaceId, @Param("userId") long userId);
    @Select("SELECT id FROM roles WHERE code = 'OWNER' AND system_role = TRUE AND workspace_id IS NULL") long ownerRoleId();
    @Insert("INSERT INTO member_roles (workspace_member_id, role_id, project_id, created_at) VALUES (#{memberId}, #{roleId}, NULL, UTC_TIMESTAMP(6))")
    int insertOwnerRole(@Param("memberId") long memberId, @Param("roleId") long roleId);
    @Select("SELECT wm.id, wm.workspace_id, u.id AS user_id, u.email, u.display_name, wm.status FROM workspace_members wm JOIN users u ON u.id = wm.user_id WHERE wm.workspace_id = #{workspaceId} ORDER BY u.id")
    List<Map<String, Object>> findMembers(@Param("workspaceId") long workspaceId);
    @Select("SELECT u.id FROM users u WHERE u.status = 'ACTIVE' AND u.normalized_email = #{email}") List<Long> findActiveUserId(@Param("email") String email);
    @Select("SELECT u.id FROM users u JOIN workspace_members wm ON wm.user_id = u.id WHERE wm.workspace_id = #{workspaceId} AND wm.status = 'ACTIVE' AND u.status = 'ACTIVE' AND u.normalized_email = #{email}")
    List<Long> findActiveMemberUserId(@Param("workspaceId") long workspaceId, @Param("email") String email);
    @Select("SELECT id FROM roles WHERE code = #{roleCode} AND system_role = TRUE AND workspace_id IS NULL")
    List<Long> findSystemRoleId(@Param("roleCode") String roleCode);
    @Select("SELECT EXISTS(SELECT 1 FROM users WHERE normalized_email = #{email})")
    boolean userExists(@Param("email") String normalizedEmail);
    @Select("SELECT s.default_workspace_id FROM instance_settings s WHERE s.id = 1 AND s.initialized_at IS NOT NULL")
    List<Long> findDefaultWorkspaceId();
    @Select("SELECT o.id AS organization_id, o.name AS organization_name, "
            + "s.default_workspace_id AS workspace_id, s.default_project_id AS project_id, "
            + "EXISTS(SELECT 1 FROM workspace_members owner_member JOIN member_roles owner_assignment "
            + "ON owner_assignment.workspace_member_id = owner_member.id AND owner_assignment.project_id IS NULL "
            + "JOIN roles owner_role ON owner_role.id = owner_assignment.role_id "
            + "WHERE owner_member.workspace_id = s.default_workspace_id AND owner_member.user_id = #{userId} "
            + "AND owner_member.status = 'ACTIVE' AND owner_role.code = 'OWNER') AS owner "
            + "FROM instance_settings s JOIN organizations o ON o.id = s.default_organization_id "
            + "JOIN workspace_members wm ON wm.workspace_id = s.default_workspace_id "
            + "WHERE s.id = 1 AND s.initialized_at IS NOT NULL AND s.default_project_id IS NOT NULL "
            + "AND wm.user_id = #{userId} AND wm.status = 'ACTIVE'")
    List<Map<String, Object>> findDefaultScopeForUser(@Param("userId") long userId);
    @Insert("INSERT INTO users (email, normalized_email, display_name, password_hash, status, created_at, updated_at) VALUES (#{email}, #{normalizedEmail}, #{displayName}, #{passwordHash}, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))")
    int insertUser(@Param("email") String email, @Param("normalizedEmail") String normalizedEmail, @Param("displayName") String displayName, @Param("passwordHash") String passwordHash);
    @Insert("INSERT IGNORE INTO member_roles (workspace_member_id, role_id, project_id, created_at) SELECT wm.id, #{roleId}, NULL, UTC_TIMESTAMP(6) FROM workspace_members wm WHERE wm.workspace_id = #{workspaceId} AND wm.user_id = #{userId} AND wm.status = 'ACTIVE'")
    int assignWorkspaceRole(@Param("workspaceId") long workspaceId, @Param("userId") long userId, @Param("roleId") long roleId);
    @Insert("INSERT INTO project_members (workspace_id, project_id, user_id, status, created_at, updated_at) "
            + "SELECT default_workspace_id, default_project_id, #{userId}, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) "
            + "FROM instance_settings WHERE id = 1 AND default_workspace_id = #{workspaceId} "
            + "ON DUPLICATE KEY UPDATE status = 'ACTIVE', updated_at = UTC_TIMESTAMP(6)")
    int activateDefaultProjectMember(@Param("workspaceId") long workspaceId, @Param("userId") long userId);
    @Insert("INSERT INTO workspace_members (workspace_id, user_id, status, joined_at, created_at, updated_at, version) VALUES (#{workspaceId}, #{userId}, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0) ON DUPLICATE KEY UPDATE status = 'ACTIVE', joined_at = UTC_TIMESTAMP(6), updated_at = UTC_TIMESTAMP(6), version = version + 1")
    int activateMember(@Param("workspaceId") long workspaceId, @Param("userId") long userId);
    @Update("UPDATE workspace_members SET status = 'REMOVED', updated_at = UTC_TIMESTAMP(6), version = version + 1 WHERE workspace_id = #{workspaceId} AND user_id = #{userId} AND status = 'ACTIVE'")
    int removeMember(@Param("workspaceId") long workspaceId, @Param("userId") long userId);
}
