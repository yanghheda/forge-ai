package ai.forge.server.auth.infrastructure.persistence;

import ai.forge.server.auth.application.AuthenticationStore;
import ai.forge.server.auth.domain.CurrentUser;
import ai.forge.server.auth.domain.LoginAccount;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test-unit")
public class JdbcAuthenticationStore implements AuthenticationStore {

    /* 执行短事务认证 SQL 的 Spring JDBC 入口。 */
    private final JdbcTemplate jdbcTemplate;

    public JdbcAuthenticationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<LoginAccount> findByNormalizedEmail(String normalizedEmail) {
        return jdbcTemplate.query(
                        "SELECT id, password_hash, status, "
                                + "(locked_until IS NOT NULL AND locked_until > UTC_TIMESTAMP(6)) AS locked "
                                + "FROM users WHERE normalized_email = ?",
                        (resultSet, rowNumber) -> new LoginAccount(
                                resultSet.getLong("id"),
                                resultSet.getString("password_hash"),
                                resultSet.getString("status"),
                                resultSet.getBoolean("locked")),
                        normalizedEmail)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional
    public void recordFailure(Long userId, int lockThreshold, Duration lockDuration, String requestId) {
        if (userId != null) {
            jdbcTemplate.update(
                    "UPDATE users SET failed_login_count = failed_login_count + 1, "
                            + "locked_until = CASE WHEN failed_login_count >= ? "
                            + "THEN DATE_ADD(UTC_TIMESTAMP(6), INTERVAL ? MICROSECOND) ELSE locked_until END, "
                            + "updated_at = UTC_TIMESTAMP(6), version = version + 1 WHERE id = ?",
                    lockThreshold,
                    lockDuration.toNanos() / 1_000,
                    userId);
        }
        insertAudit(userId, "LOGIN", "FAILURE", requestId);
    }

    @Override
    @Transactional
    public void recordRejected(Long userId, String requestId) {
        insertAudit(userId, "LOGIN", "FAILURE", requestId);
    }

    @Override
    @Transactional
    public void recordSuccess(long userId, String requestId) {
        jdbcTemplate.update(
                "UPDATE users SET failed_login_count = 0, locked_until = NULL, last_login_at = UTC_TIMESTAMP(6), "
                        + "updated_at = UTC_TIMESTAMP(6), version = version + 1 WHERE id = ?",
                userId);
        insertAudit(userId, "LOGIN", "SUCCESS", requestId);
    }

    @Override
    @Transactional
    public void recordLogout(long userId, String requestId) {
        insertAudit(userId, "SESSION_REVOKED", "SUCCESS", requestId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CurrentUser> findCurrentUser(long userId) {
        List<UserRow> users = jdbcTemplate.query(
                "SELECT id, email, display_name FROM users WHERE id = ? AND status = 'ACTIVE'",
                (resultSet, rowNumber) -> new UserRow(
                        resultSet.getLong("id"), resultSet.getString("email"), resultSet.getString("display_name")),
                userId);
        if (users.isEmpty()) {
            return Optional.empty();
        }

        Map<Long, WorkspaceBuilder> workspaces = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT w.id, w.slug, w.name, r.code FROM workspace_members wm "
                        + "JOIN workspaces w ON w.id = wm.workspace_id AND w.status = 'ACTIVE' "
                        + "LEFT JOIN member_roles mr ON mr.workspace_member_id = wm.id AND mr.project_id IS NULL "
                        + "LEFT JOIN roles r ON r.id = mr.role_id "
                        + "WHERE wm.user_id = ? AND wm.status = 'ACTIVE' ORDER BY w.id, r.code",
                resultSet -> {
                    long workspaceId = resultSet.getLong("id");
                    WorkspaceBuilder builder = workspaces.get(workspaceId);
                    if (builder == null) {
                        builder = new WorkspaceBuilder(
                                workspaceId, resultSet.getString("slug"), resultSet.getString("name"));
                        workspaces.put(workspaceId, builder);
                    }
                    String role = resultSet.getString("code");
                    if (role != null) {
                        builder.roles().add(role);
                    }
                },
                userId);
        UserRow user = users.getFirst();
        List<CurrentUser.WorkspaceAccess> access = workspaces.values().stream()
                .map(builder -> new CurrentUser.WorkspaceAccess(
                        builder.id(), builder.slug(), builder.name(), List.copyOf(builder.roles())))
                .toList();
        return Optional.of(new CurrentUser(user.id(), user.email(), user.displayName(), access));
    }

    private void insertAudit(Long userId, String action, String result, String requestId) {
        jdbcTemplate.update(
                "INSERT INTO audit_logs "
                        + "(workspace_id, project_id, actor_type, actor_id, action, resource_type, resource_id, "
                        + "result, request_id, run_id, metadata_redacted_json, created_at) "
                        + "VALUES (NULL, NULL, ?, ?, ?, 'SESSION', ?, ?, ?, NULL, JSON_OBJECT(), UTC_TIMESTAMP(6))",
                userId == null ? "ANONYMOUS" : "USER",
                userId,
                action,
                userId == null ? 0L : userId,
                result,
                requestId);
    }

    private record UserRow(
            /* 当前用户的数据库标识。 */
            long id,
            /* 当前用户保留原始大小写的展示邮箱。 */
            String email,
            /* 当前用户的界面展示名称。 */
            String displayName) {}

    private record WorkspaceBuilder(
            /* 正在聚合角色的 Workspace 标识。 */
            long id,
            /* 正在聚合角色的 Workspace 路由短名。 */
            String slug,
            /* 正在聚合角色的 Workspace 展示名称。 */
            String name,
            /* 从多行关联结果收集的 Workspace 级角色代码。 */
            List<String> roles) {

        private WorkspaceBuilder(long id, String slug, String name) {
            this(id, slug, name, new ArrayList<>());
        }
    }
}
