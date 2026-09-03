package ai.forge.server.project.infrastructure.persistence;

import ai.forge.server.project.application.ProjectStore;
import ai.forge.server.project.domain.Project;
import ai.forge.server.project.domain.ProjectKeyConflictException;
import ai.forge.server.project.domain.ProjectMember;
import ai.forge.server.project.domain.ProjectStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository @Profile("!test-unit")
public class MybatisProjectStore implements ProjectStore {
    /* 执行带 Workspace 与 Project 范围 SQL 的 MyBatis Mapper。 */ private final ProjectMapper mapper;
    public MybatisProjectStore(ProjectMapper mapper) { this.mapper = mapper; }
    @Override @Transactional public Project createWithCreatorMembership(long workspaceId, long creatorUserId, String key, String name, String description) { try { mapper.insertProject(workspaceId, creatorUserId, key, name, description); } catch (DuplicateKeyException exception) { throw new ProjectKeyConflictException(); } long projectId = mapper.lastInsertId(); mapper.insertCreatorMember(workspaceId, projectId, creatorUserId); mapper.insertItemSequence(projectId); return findByIdAndWorkspaceId(projectId, workspaceId).orElseThrow(); }
    @Override public List<Project> findByWorkspaceId(long workspaceId) { return mapper.findByWorkspaceId(workspaceId).stream().map(this::project).toList(); }
    @Override public Optional<Project> findByIdAndWorkspaceId(long projectId, long workspaceId) { return mapper.findByIdAndWorkspaceId(projectId, workspaceId).stream().findFirst().map(this::project); }
    @Override public boolean hasActiveMember(long workspaceId, long projectId, long userId) { return mapper.hasActiveMember(workspaceId, projectId, userId); }
    @Override public boolean archive(long workspaceId, long projectId, long expectedVersion) { return mapper.archive(workspaceId, projectId, expectedVersion) == 1; }
    @Override public List<ProjectMember> findMembersByProjectIdAndWorkspaceId(long projectId, long workspaceId) { return mapper.findMembers(projectId, workspaceId).stream().map(row -> new ProjectMember(number(row,"id"), number(row,"workspace_id"), number(row,"project_id"), number(row,"user_id"), text(row,"email"), text(row,"display_name"), "ACTIVE".equals(text(row,"status")))).toList(); }
    @Override public void activateMember(long workspaceId, long projectId, long userId) { mapper.activateMember(workspaceId, projectId, userId); }
    @Override public void removeMember(long workspaceId, long projectId, long userId) { mapper.removeMember(workspaceId, projectId, userId); }
    private Project project(Map<String,Object> row) { return new Project(number(row,"id"), number(row,"workspace_id"), text(row,"key"), text(row,"name"), text(row,"description"), ProjectStatus.valueOf(text(row,"status")), instant(row.get("archived_at")), number(row,"version"), instant(row.get("created_at")), instant(row.get("updated_at"))); }
    private Instant instant(Object value) { return value == null ? null : ((LocalDateTime) value).toInstant(ZoneOffset.UTC); }
    private long number(Map<String,Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private String text(Map<String,Object> row, String key) { return row.get(key).toString(); }
}
