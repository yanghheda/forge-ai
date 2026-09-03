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
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test-unit")
public class MybatisAuthenticationStore implements AuthenticationStore {

    /* 执行认证与会话审计 SQL 的 MyBatis Mapper。 */
    private final AuthenticationMapper authenticationMapper;

    public MybatisAuthenticationStore(AuthenticationMapper authenticationMapper) {
        this.authenticationMapper = authenticationMapper;
    }

    @Override
    public Optional<LoginAccount> findByNormalizedEmail(String normalizedEmail) {
        return authenticationMapper.findLoginAccount(normalizedEmail).stream().findFirst().map(row -> new LoginAccount(
                number(row, "id"), text(row, "password_hash"), text(row, "status"), booleanValue(row, "locked")));
    }

    @Override
    @Transactional
    public void recordFailure(Long userId, int lockThreshold, Duration lockDuration, String requestId) {
        if (userId != null) {
            authenticationMapper.recordFailure(userId, lockThreshold, lockDuration.toNanos() / 1_000);
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
        authenticationMapper.recordSuccess(userId);
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
        List<Map<String, Object>> users = authenticationMapper.findCurrentUser(userId);
        if (users.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, WorkspaceBuilder> workspaces = new LinkedHashMap<>();
        for (Map<String, Object> row : authenticationMapper.findWorkspaceAccess(userId)) {
            long workspaceId = number(row, "id");
            WorkspaceBuilder builder = workspaces.computeIfAbsent(
                    workspaceId, ignored -> new WorkspaceBuilder(workspaceId, text(row, "slug"), text(row, "name")));
            String role = nullableText(row, "code");
            if (role != null) {
                builder.roles().add(role);
            }
        }
        Map<String, Object> user = users.getFirst();
        List<CurrentUser.WorkspaceAccess> access = workspaces.values().stream()
                .map(value -> new CurrentUser.WorkspaceAccess(value.id(), value.slug(), value.name(), List.copyOf(value.roles())))
                .toList();
        return Optional.of(new CurrentUser(number(user, "id"), text(user, "email"), text(user, "display_name"), access));
    }

    private void insertAudit(Long userId, String action, String result, String requestId) {
        authenticationMapper.insertAudit(userId, userId == null ? "ANONYMOUS" : "USER", action,
                userId == null ? 0L : userId, result, requestId);
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private boolean booleanValue(Map<String, Object> row, String key) {
        return Boolean.TRUE.equals(row.get(key)) || row.get(key) instanceof Number number && number.intValue() != 0;
    }

    private String text(Map<String, Object> row, String key) {
        return row.get(key).toString();
    }

    private String nullableText(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private record WorkspaceBuilder(
            /* 正在聚合角色的 Workspace 标识。 */ long id,
            /* 正在聚合角色的 Workspace 路由短名。 */ String slug,
            /* 正在聚合角色的 Workspace 展示名称。 */ String name,
            /* 从关联结果收集的 Workspace 级角色代码。 */ List<String> roles) {
        private WorkspaceBuilder(long id, String slug, String name) {
            this(id, slug, name, new ArrayList<>());
        }
    }
}
