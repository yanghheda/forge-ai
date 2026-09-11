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
            INSERT INTO organization_members
                (organization_id, user_id, status, joined_at, created_at, updated_at)
            VALUES
                (#{organizationId}, #{userId}, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
            """)
    int insertOrganizationMember(@Param("organizationId") long organizationId, @Param("userId") long userId);

    @Select("SELECT id FROM roles WHERE code = 'OWNER' AND system_role = TRUE AND organization_id IS NULL")
    long findOwnerRoleId();

    @Insert("""
            INSERT INTO member_roles (organization_member_id, role_id, created_at)
            VALUES (#{organizationMemberId}, #{roleId}, UTC_TIMESTAMP(6))
            """)
    int insertMemberRole(@Param("organizationMemberId") long organizationMemberId, @Param("roleId") long roleId);

    @Insert("INSERT INTO organization_item_sequences (organization_id, next_value, version) VALUES (#{organizationId}, 1, 0)")
    int insertOrganizationSequence(@Param("organizationId") long organizationId);

    @Insert("""
            INSERT INTO organization_policies
                (organization_id, allow_skip_ux, ci_required, updated_by, updated_at, version)
            VALUES (#{organizationId}, FALSE, TRUE, #{userId}, UTC_TIMESTAMP(6), 0)
            """)
    int insertOrganizationPolicy(@Param("organizationId") long organizationId, @Param("userId") long userId);

    @Insert("""
            INSERT INTO audit_logs
                (organization_id, actor_type, actor_id, action, resource_type, resource_id,
                 result, request_id, run_id, metadata_redacted_json, created_at)
            VALUES
                (#{organizationId}, 'USER', #{userId}, 'INSTANCE_INITIALIZED', 'INSTANCE', 1,
                 'SUCCESS', #{requestId}, NULL,
                 JSON_OBJECT('organizationSlug', #{organizationSlug}),
                 UTC_TIMESTAMP(6))
            """)
    int insertBootstrapAudit(
            @Param("organizationId") long organizationId,
            @Param("userId") long userId,
            @Param("requestId") String requestId,
            @Param("organizationSlug") String organizationSlug);

    @Update("""
            UPDATE instance_settings
            SET initialized_at = UTC_TIMESTAMP(6), default_organization_id = #{organizationId},
                version = version + 1
            WHERE id = 1 AND initialized_at IS NULL
            """)
    int markInitialized(@Param("organizationId") long organizationId);
}
