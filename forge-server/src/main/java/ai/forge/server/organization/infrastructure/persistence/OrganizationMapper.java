package ai.forge.server.organization.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrganizationMapper {
    @Select("SELECT o.id AS organization_id, o.name AS organization_name, "
            + "EXISTS(SELECT 1 FROM member_roles mr JOIN roles r ON r.id=mr.role_id "
            + "WHERE mr.organization_member_id=om.id AND r.code='OWNER') AS owner "
            + "FROM organization_members om JOIN organizations o ON o.id=om.organization_id "
            + "WHERE om.user_id=#{userId} AND om.status='ACTIVE'")
    List<Map<String, Object>> findContextForUser(@Param("userId") long userId);

    @Select("SELECT default_organization_id FROM instance_settings WHERE id=1 AND initialized_at IS NOT NULL")
    List<Long> findOrganizationId();

    @Select("SELECT EXISTS(SELECT 1 FROM users WHERE normalized_email=#{email})")
    boolean userExists(@Param("email") String normalizedEmail);

    @Select("SELECT id FROM roles WHERE code=#{roleCode} AND system_role=TRUE AND organization_id IS NULL")
    List<Long> findSystemRoleId(@Param("roleCode") String roleCode);

    @Insert("INSERT INTO users (email,normalized_email,display_name,password_hash,status,created_at,updated_at) "
            + "VALUES (#{email},#{normalizedEmail},#{displayName},#{passwordHash},'PENDING',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")
    int insertPendingUser(
            @Param("email") String email,
            @Param("normalizedEmail") String normalizedEmail,
            @Param("displayName") String displayName,
            @Param("passwordHash") String passwordHash);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Insert("INSERT INTO organization_members (organization_id,user_id,status,joined_at,created_at,updated_at,version) "
            + "VALUES (#{organizationId},#{userId},'PENDING',NULL,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)")
    int insertPendingMember(@Param("organizationId") long organizationId, @Param("userId") long userId);

    @Insert("INSERT INTO member_roles (organization_member_id,role_id,created_at) "
            + "VALUES (#{memberId},#{roleId},UTC_TIMESTAMP(6))")
    int insertRole(@Param("memberId") long memberId, @Param("roleId") long roleId);

    @Select("SELECT u.id AS user_id,u.display_name,u.email,om.status,u.last_login_at,om.version,"
            + "GROUP_CONCAT(DISTINCT r.code ORDER BY r.code) AS roles "
            + "FROM organization_members om JOIN users u ON u.id=om.user_id "
            + "LEFT JOIN member_roles mr ON mr.organization_member_id=om.id LEFT JOIN roles r ON r.id=mr.role_id "
            + "WHERE om.organization_id=#{organizationId} GROUP BY u.id,u.display_name,u.email,om.status,u.last_login_at,om.version "
            + "ORDER BY FIELD(om.status,'PENDING','ACTIVE','DISABLED'),u.display_name,u.id")
    List<Map<String, Object>> findMembers(@Param("organizationId") long organizationId);

    @Select("SELECT u.id AS user_id,u.display_name,u.email,om.status,u.last_login_at,om.version,"
            + "GROUP_CONCAT(DISTINCT r.code ORDER BY r.code) AS roles "
            + "FROM organization_members om JOIN users u ON u.id=om.user_id "
            + "LEFT JOIN member_roles mr ON mr.organization_member_id=om.id LEFT JOIN roles r ON r.id=mr.role_id "
            + "WHERE om.organization_id=#{organizationId} AND om.user_id=#{userId} "
            + "GROUP BY u.id,u.display_name,u.email,om.status,u.last_login_at,om.version")
    List<Map<String, Object>> findMember(
            @Param("organizationId") long organizationId, @Param("userId") long userId);

    @Update("UPDATE organization_members SET status=#{status},joined_at=CASE WHEN #{status}='ACTIVE' "
            + "THEN COALESCE(joined_at,UTC_TIMESTAMP(6)) ELSE joined_at END,updated_at=UTC_TIMESTAMP(6),version=version+1 "
            + "WHERE organization_id=#{organizationId} AND user_id=#{userId} AND version=#{expectedVersion}")
    int updateMember(
            @Param("organizationId") long organizationId,
            @Param("userId") long userId,
            @Param("status") String status,
            @Param("expectedVersion") long expectedVersion);

    @Update("UPDATE users SET status=#{status},updated_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=#{userId}")
    int updateUserStatus(@Param("userId") long userId, @Param("status") String status);

    @Select("SELECT id FROM organization_members WHERE organization_id=#{organizationId} AND user_id=#{userId}")
    List<Long> findMemberId(@Param("organizationId") long organizationId, @Param("userId") long userId);

    @Delete("DELETE mr FROM member_roles mr JOIN roles r ON r.id=mr.role_id "
            + "WHERE mr.organization_member_id=#{memberId} AND r.code NOT IN ('OWNER','ADMIN')")
    int deleteBusinessRoles(@Param("memberId") long memberId);

}
