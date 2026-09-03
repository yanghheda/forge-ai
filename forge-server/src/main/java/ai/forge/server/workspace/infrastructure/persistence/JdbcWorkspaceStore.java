package ai.forge.server.workspace.infrastructure.persistence;

import ai.forge.server.workspace.application.WorkspaceStore;
import ai.forge.server.workspace.domain.Workspace;
import ai.forge.server.workspace.domain.WorkspaceMember;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test-unit")
public class JdbcWorkspaceStore implements WorkspaceStore {

    /* 通过参数化 SQL 访问 Workspace、成员关系和初始 Owner 事实。 */
    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkspaceStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Workspace> findActiveByUserId(long userId) {
        return jdbcTemplate.query(
                "SELECT w.id, w.organization_id, w.name, w.slug FROM workspaces w "
                        + "JOIN workspace_members wm ON wm.workspace_id = w.id "
                        + "WHERE wm.user_id = ? AND wm.status = 'ACTIVE' AND w.status = 'ACTIVE' ORDER BY w.id",
                (resultSet, rowNumber) -> workspace(resultSet.getLong("id"), resultSet.getLong("organization_id"),
                        resultSet.getString("name"), resultSet.getString("slug")),
                userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Workspace> findActiveByIdAndUserId(long workspaceId, long userId) {
        return jdbcTemplate.query(
                        "SELECT w.id, w.organization_id, w.name, w.slug FROM workspaces w "
                                + "JOIN workspace_members wm ON wm.workspace_id = w.id "
                                + "WHERE w.id = ? AND wm.user_id = ? AND wm.status = 'ACTIVE' AND w.status = 'ACTIVE'",
                        (resultSet, rowNumber) -> workspace(
                                resultSet.getLong("id"), resultSet.getLong("organization_id"),
                                resultSet.getString("name"), resultSet.getString("slug")),
                        workspaceId,
                        userId)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Workspace> findActiveBySlugAndUserId(String slug, long userId) {
        return jdbcTemplate.query(
                        "SELECT w.id, w.organization_id, w.name, w.slug FROM workspaces w "
                                + "JOIN workspace_members wm ON wm.workspace_id = w.id "
                                + "WHERE w.slug = ? AND wm.user_id = ? AND wm.status = 'ACTIVE' AND w.status = 'ACTIVE'",
                        (resultSet, rowNumber) -> workspace(
                                resultSet.getLong("id"), resultSet.getLong("organization_id"),
                                resultSet.getString("name"), resultSet.getString("slug")),
                        slug,
                        userId)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveOwner(long workspaceId, long userId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM workspace_members wm "
                        + "JOIN member_roles mr ON mr.workspace_member_id = wm.id AND mr.project_id IS NULL "
                        + "JOIN roles r ON r.id = mr.role_id "
                        + "WHERE wm.workspace_id = ? AND wm.user_id = ? AND wm.status = 'ACTIVE' "
                        + "AND r.code = 'OWNER' AND r.system_role = TRUE)",
                Boolean.class,
                workspaceId,
                userId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAnyActiveOwnerRole(long userId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM workspace_members wm "
                        + "JOIN member_roles mr ON mr.workspace_member_id = wm.id AND mr.project_id IS NULL "
                        + "JOIN roles r ON r.id = mr.role_id "
                        + "WHERE wm.user_id = ? AND wm.status = 'ACTIVE' AND r.code = 'OWNER' AND r.system_role = TRUE)",
                Boolean.class,
                userId));
    }

    @Override
    @Transactional
    public Workspace createForOwner(long ownerUserId, String name, String slug) {
        Long organizationId = jdbcTemplate.queryForObject(
                "SELECT default_organization_id FROM instance_settings WHERE id = 1 AND initialized_at IS NOT NULL",
                Long.class);
        jdbcTemplate.update(
                "INSERT INTO workspaces (organization_id, name, slug, status, settings_json, created_at, updated_at, version) "
                        + "VALUES (?, ?, ?, 'ACTIVE', JSON_OBJECT(), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)",
                organizationId,
                name,
                slug);
        long workspaceId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                "INSERT INTO workspace_members (workspace_id, user_id, status, joined_at, created_at, updated_at, version) "
                        + "VALUES (?, ?, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)",
                workspaceId,
                ownerUserId);
        long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        long ownerRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE code = 'OWNER' AND system_role = TRUE AND workspace_id IS NULL", Long.class);
        jdbcTemplate.update(
                "INSERT INTO member_roles (workspace_member_id, role_id, project_id, created_at) "
                        + "VALUES (?, ?, NULL, UTC_TIMESTAMP(6))",
                memberId,
                ownerRoleId);
        return new Workspace(workspaceId, organizationId, name, slug, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceMember> findMembersByWorkspaceId(long workspaceId) {
        return jdbcTemplate.query(
                "SELECT wm.id, wm.workspace_id, u.id AS user_id, u.email, u.display_name, wm.status "
                        + "FROM workspace_members wm JOIN users u ON u.id = wm.user_id "
                        + "WHERE wm.workspace_id = ? ORDER BY u.id",
                (resultSet, rowNumber) -> new WorkspaceMember(
                        resultSet.getLong("id"),
                        resultSet.getLong("workspace_id"),
                        resultSet.getLong("user_id"),
                        resultSet.getString("email"),
                        resultSet.getString("display_name"),
                        "ACTIVE".equals(resultSet.getString("status"))),
                workspaceId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findActiveUserIdByNormalizedEmail(String normalizedEmail) {
        return jdbcTemplate.query(
                        "SELECT u.id FROM users u WHERE u.status = 'ACTIVE' AND u.normalized_email = ?",
                        (resultSet, rowNumber) -> resultSet.getLong("id"),
                        normalizedEmail)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findActiveMemberUserIdByNormalizedEmail(long workspaceId, String normalizedEmail) {
        return jdbcTemplate.query(
                        "SELECT u.id FROM users u JOIN workspace_members wm ON wm.user_id = u.id "
                                + "WHERE wm.workspace_id = ? AND wm.status = 'ACTIVE' AND u.status = 'ACTIVE' "
                                + "AND u.normalized_email = ?",
                        (resultSet, rowNumber) -> resultSet.getLong("id"),
                        workspaceId,
                        normalizedEmail)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional
    public void activateMember(long workspaceId, long userId) {
        jdbcTemplate.update(
                "INSERT INTO workspace_members (workspace_id, user_id, status, joined_at, created_at, updated_at, version) "
                        + "VALUES (?, ?, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0) "
                        + "ON DUPLICATE KEY UPDATE status = 'ACTIVE', joined_at = UTC_TIMESTAMP(6), "
                        + "updated_at = UTC_TIMESTAMP(6), version = version + 1",
                workspaceId,
                userId);
    }

    @Override
    @Transactional
    public void removeMember(long workspaceId, long userId) {
        jdbcTemplate.update(
                "UPDATE workspace_members SET status = 'REMOVED', updated_at = UTC_TIMESTAMP(6), version = version + 1 "
                        + "WHERE workspace_id = ? AND user_id = ? AND status = 'ACTIVE'",
                workspaceId,
                userId);
    }

    private Workspace workspace(long id, long organizationId, String name, String slug) {
        return new Workspace(id, organizationId, name, slug, true);
    }
}
