package ai.forge.server.auth.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AuthenticationMapper {

    @Select("SELECT id, password_hash, status, (locked_until IS NOT NULL AND locked_until > UTC_TIMESTAMP(6)) AS locked FROM users WHERE normalized_email = #{email}")
    List<Map<String, Object>> findLoginAccount(@Param("email") String normalizedEmail);

    @Update("UPDATE users SET failed_login_count = failed_login_count + 1, locked_until = CASE WHEN failed_login_count >= #{threshold} THEN DATE_ADD(UTC_TIMESTAMP(6), INTERVAL #{lockMicros} MICROSECOND) ELSE locked_until END, updated_at = UTC_TIMESTAMP(6), version = version + 1 WHERE id = #{userId}")
    int recordFailure(@Param("userId") long userId, @Param("threshold") int threshold, @Param("lockMicros") long lockMicros);

    @Update("UPDATE users SET failed_login_count = 0, locked_until = NULL, last_login_at = UTC_TIMESTAMP(6), updated_at = UTC_TIMESTAMP(6), version = version + 1 WHERE id = #{userId}")
    int recordSuccess(@Param("userId") long userId);

    @Select("SELECT id, email, display_name FROM users WHERE id = #{userId} AND status = 'ACTIVE'")
    List<Map<String, Object>> findCurrentUser(@Param("userId") long userId);

    @Select("SELECT w.id, w.slug, w.name, r.code FROM workspace_members wm JOIN workspaces w ON w.id = wm.workspace_id AND w.status = 'ACTIVE' LEFT JOIN member_roles mr ON mr.workspace_member_id = wm.id AND mr.project_id IS NULL LEFT JOIN roles r ON r.id = mr.role_id WHERE wm.user_id = #{userId} AND wm.status = 'ACTIVE' ORDER BY w.id, r.code")
    List<Map<String, Object>> findWorkspaceAccess(@Param("userId") long userId);

    @Insert("INSERT INTO audit_logs (workspace_id, project_id, actor_type, actor_id, action, resource_type, resource_id, result, request_id, run_id, metadata_redacted_json, created_at) VALUES (NULL, NULL, #{actorType}, #{userId}, #{action}, 'SESSION', #{resourceId}, #{result}, #{requestId}, NULL, JSON_OBJECT(), UTC_TIMESTAMP(6))")
    int insertAudit(@Param("userId") Long userId, @Param("actorType") String actorType, @Param("action") String action, @Param("resourceId") long resourceId, @Param("result") String result, @Param("requestId") String requestId);
}
