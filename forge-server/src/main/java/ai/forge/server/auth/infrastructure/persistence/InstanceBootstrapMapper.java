package ai.forge.server.auth.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InstanceBootstrapMapper extends BaseMapper<InstanceSettingsEntity> {

    @Select("SELECT initialized_at IS NOT NULL FROM instance_settings WHERE id = 1")
    boolean isInitialized();

    @Select("SELECT initialized_at FROM instance_settings WHERE id = 1 FOR UPDATE")
    LocalDateTime lockAndGetInitializedAt();

    @Insert("""
            INSERT INTO users
                (email, normalized_email, display_name, password_hash, status, created_at, updated_at)
            VALUES
                (#{email}, #{normalizedEmail}, #{displayName}, #{passwordHash}, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
            """)
    int insertUser(
            @Param("email") String email,
            @Param("normalizedEmail") String normalizedEmail,
            @Param("displayName") String displayName,
            @Param("passwordHash") String passwordHash);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Insert("""
            INSERT INTO organizations (name, slug, owner_user_id, created_at, updated_at)
            VALUES (#{name}, #{slug}, #{ownerUserId}, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
            """)
    int insertOrganization(
            @Param("name") String name,
            @Param("slug") String slug,
            @Param("ownerUserId") long ownerUserId);

    @Insert("""
            INSERT INTO workspaces
                (organization_id, name, slug, status, settings_json, created_at, updated_at)
            VALUES
                (#{organizationId}, #{name}, #{slug}, 'ACTIVE', JSON_OBJECT(), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
            """)
    int insertWorkspace(
            @Param("organizationId") long organizationId,
            @Param("name") String name,
            @Param("slug") String slug);

    @Insert("""
            INSERT INTO projects
                (workspace_id, `key`, name, description, status, created_by, created_at, updated_at, version)
            VALUES
                (#{workspaceId}, 'REQ', #{name}, '单组织产品模型的内部默认需求范围', 'ACTIVE', #{createdBy},
                 UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
            """)
    int insertDefaultProject(
            @Param("workspaceId") long workspaceId,
            @Param("name") String name,
            @Param("createdBy") long createdBy);

    @Insert("INSERT INTO project_item_sequences (project_id, next_value, version) VALUES (#{projectId}, 1, 0)")
    int insertProjectSequence(@Param("projectId") long projectId);

    @Insert("""
            INSERT INTO workspace_members
                (workspace_id, user_id, status, joined_at, created_at, updated_at)
            VALUES
                (#{workspaceId}, #{userId}, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
            """)
    int insertWorkspaceMember(@Param("workspaceId") long workspaceId, @Param("userId") long userId);

    @Select("SELECT id FROM roles WHERE code = 'OWNER' AND system_role = TRUE AND workspace_id IS NULL")
    long findOwnerRoleId();

    @Insert("""
            INSERT INTO member_roles (workspace_member_id, role_id, project_id, created_at)
            VALUES (#{workspaceMemberId}, #{roleId}, NULL, UTC_TIMESTAMP(6))
            """)
    int insertMemberRole(@Param("workspaceMemberId") long workspaceMemberId, @Param("roleId") long roleId);

    @Insert("""
            INSERT INTO audit_logs
                (workspace_id, project_id, actor_type, actor_id, action, resource_type, resource_id,
                 result, request_id, run_id, metadata_redacted_json, created_at)
            VALUES
                (#{workspaceId}, NULL, 'USER', #{userId}, 'INSTANCE_INITIALIZED', 'INSTANCE', 1,
                 'SUCCESS', #{requestId}, NULL,
                 JSON_OBJECT('organizationSlug', #{organizationSlug}, 'workspaceSlug', #{workspaceSlug}),
                 UTC_TIMESTAMP(6))
            """)
    int insertBootstrapAudit(
            @Param("workspaceId") long workspaceId,
            @Param("userId") long userId,
            @Param("requestId") String requestId,
            @Param("organizationSlug") String organizationSlug,
            @Param("workspaceSlug") String workspaceSlug);

    @Update("""
            UPDATE instance_settings
            SET initialized_at = UTC_TIMESTAMP(6), default_organization_id = #{organizationId},
                default_workspace_id = #{workspaceId}, default_project_id = #{projectId}, version = version + 1
            WHERE id = 1 AND initialized_at IS NULL
            """)
    int markInitialized(
            @Param("organizationId") long organizationId,
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId);
}
