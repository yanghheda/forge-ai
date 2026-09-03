package ai.forge.server.project.infrastructure.persistence;

import ai.forge.server.project.application.ProjectStore;
import ai.forge.server.project.domain.Project;
import ai.forge.server.project.domain.ProjectKeyConflictException;
import ai.forge.server.project.domain.ProjectMember;
import ai.forge.server.project.domain.ProjectStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test-unit")
public class JdbcProjectStore implements ProjectStore {

    /* 通过带 Workspace 条件的参数化 SQL 读写项目事实和成员范围。 */
    private final JdbcTemplate jdbcTemplate;

    public JdbcProjectStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public Project createWithCreatorMembership(
            long workspaceId, long creatorUserId, String key, String name, String description) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO projects (workspace_id, `key`, name, description, status, created_by, created_at, updated_at, archived_at, version) "
                            + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL, 0)",
                    workspaceId,
                    key,
                    name,
                    description,
                    creatorUserId);
        } catch (DuplicateKeyException exception) {
            throw new ProjectKeyConflictException();
        }
        long projectId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                "INSERT INTO project_members (workspace_id, project_id, user_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                workspaceId,
                projectId,
                creatorUserId);
        return findByIdAndWorkspaceId(projectId, workspaceId).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Project> findByWorkspaceId(long workspaceId) {
        return jdbcTemplate.query(
                projectSelect() + " WHERE p.workspace_id = ? ORDER BY p.id",
                (resultSet, rowNumber) -> project(
                        resultSet.getLong("id"),
                        resultSet.getLong("workspace_id"),
                        resultSet.getString("key"),
                        resultSet.getString("name"),
                        resultSet.getString("description"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("archived_at"),
                        resultSet.getLong("version"),
                        resultSet.getTimestamp("created_at"),
                        resultSet.getTimestamp("updated_at")),
                workspaceId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Project> findByIdAndWorkspaceId(long projectId, long workspaceId) {
        return jdbcTemplate.query(
                        projectSelect() + " WHERE p.id = ? AND p.workspace_id = ?",
                        (resultSet, rowNumber) -> project(
                                resultSet.getLong("id"),
                                resultSet.getLong("workspace_id"),
                                resultSet.getString("key"),
                                resultSet.getString("name"),
                                resultSet.getString("description"),
                                resultSet.getString("status"),
                                resultSet.getTimestamp("archived_at"),
                                resultSet.getLong("version"),
                                resultSet.getTimestamp("created_at"),
                                resultSet.getTimestamp("updated_at")),
                        projectId,
                        workspaceId)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveMember(long workspaceId, long projectId, long userId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM project_members pm "
                        + "JOIN workspace_members wm ON wm.workspace_id = pm.workspace_id AND wm.user_id = pm.user_id "
                        + "WHERE pm.workspace_id = ? AND pm.project_id = ? AND pm.user_id = ? "
                        + "AND pm.status = 'ACTIVE' AND wm.status = 'ACTIVE')",
                Boolean.class,
                workspaceId,
                projectId,
                userId));
    }

    @Override
    @Transactional
    public boolean archive(long workspaceId, long projectId, long expectedVersion) {
        return jdbcTemplate.update(
                "UPDATE projects SET status = 'ARCHIVED', archived_at = UTC_TIMESTAMP(6), updated_at = UTC_TIMESTAMP(6), "
                        + "version = version + 1 WHERE id = ? AND workspace_id = ? AND status = 'ACTIVE' AND version = ?",
                projectId,
                workspaceId,
                expectedVersion) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMember> findMembersByProjectIdAndWorkspaceId(long projectId, long workspaceId) {
        return jdbcTemplate.query(
                "SELECT pm.id, pm.workspace_id, pm.project_id, u.id AS user_id, u.email, u.display_name, pm.status "
                        + "FROM project_members pm JOIN users u ON u.id = pm.user_id "
                        + "WHERE pm.project_id = ? AND pm.workspace_id = ? ORDER BY u.id",
                (resultSet, rowNumber) -> new ProjectMember(
                        resultSet.getLong("id"),
                        resultSet.getLong("workspace_id"),
                        resultSet.getLong("project_id"),
                        resultSet.getLong("user_id"),
                        resultSet.getString("email"),
                        resultSet.getString("display_name"),
                        "ACTIVE".equals(resultSet.getString("status"))),
                projectId,
                workspaceId);
    }

    @Override
    @Transactional
    public void activateMember(long workspaceId, long projectId, long userId) {
        jdbcTemplate.update(
                "INSERT INTO project_members (workspace_id, project_id, user_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)) "
                        + "ON DUPLICATE KEY UPDATE status = 'ACTIVE', updated_at = UTC_TIMESTAMP(6)",
                workspaceId,
                projectId,
                userId);
    }

    @Override
    @Transactional
    public void removeMember(long workspaceId, long projectId, long userId) {
        jdbcTemplate.update(
                "UPDATE project_members SET status = 'REMOVED', updated_at = UTC_TIMESTAMP(6) "
                        + "WHERE workspace_id = ? AND project_id = ? AND user_id = ? AND status = 'ACTIVE'",
                workspaceId,
                projectId,
                userId);
    }

    private String projectSelect() {
        return "SELECT p.id, p.workspace_id, p.`key` AS `key`, p.name, p.description, p.status, p.archived_at, "
                + "p.version, p.created_at, p.updated_at FROM projects p";
    }

    private Project project(
            long id,
            long workspaceId,
            String key,
            String name,
            String description,
            String status,
            Timestamp archivedAt,
            long version,
            Timestamp createdAt,
            Timestamp updatedAt) {
        return new Project(
                id,
                workspaceId,
                key,
                name,
                description,
                ProjectStatus.valueOf(status),
                asInstant(archivedAt),
                version,
                asInstant(createdAt),
                asInstant(updatedAt));
    }

    private Instant asInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
