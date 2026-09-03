package ai.forge.server.workspace.infrastructure.persistence;

import ai.forge.server.workspace.application.WorkspaceStore;
import ai.forge.server.workspace.domain.Workspace;
import ai.forge.server.workspace.domain.WorkspaceMember;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository @Profile("!test-unit")
public class MybatisWorkspaceStore implements WorkspaceStore {
    /* 执行带 Workspace 范围 SQL 的 MyBatis Mapper。 */ private final WorkspaceMapper mapper;
    public MybatisWorkspaceStore(WorkspaceMapper mapper) { this.mapper = mapper; }
    @Override @Transactional(readOnly = true) public List<Workspace> findActiveByUserId(long userId) { return mapper.findActiveByUserId(userId).stream().map(this::workspace).toList(); }
    @Override @Transactional(readOnly = true) public Optional<Workspace> findActiveByIdAndUserId(long workspaceId, long userId) { return mapper.findActiveByIdAndUserId(workspaceId, userId).stream().findFirst().map(this::workspace); }
    @Override @Transactional(readOnly = true) public Optional<Workspace> findActiveBySlugAndUserId(String slug, long userId) { return mapper.findActiveBySlugAndUserId(slug, userId).stream().findFirst().map(this::workspace); }
    @Override public boolean isActiveOwner(long workspaceId, long userId) { return mapper.isActiveOwner(workspaceId, userId); }
    @Override public boolean hasAnyActiveOwnerRole(long userId) { return mapper.hasAnyActiveOwnerRole(userId); }
    @Override @Transactional public Workspace createForOwner(long ownerUserId, String name, String slug) { long organizationId = mapper.defaultOrganizationId(); mapper.insertWorkspace(organizationId, name, slug); long workspaceId = mapper.lastInsertId(); mapper.insertMember(workspaceId, ownerUserId); long memberId = mapper.lastInsertId(); mapper.insertOwnerRole(memberId, mapper.ownerRoleId()); return new Workspace(workspaceId, organizationId, name, slug, true); }
    @Override public List<WorkspaceMember> findMembersByWorkspaceId(long workspaceId) { return mapper.findMembers(workspaceId).stream().map(row -> new WorkspaceMember(number(row,"id"), number(row,"workspace_id"), number(row,"user_id"), text(row,"email"), text(row,"display_name"), "ACTIVE".equals(text(row,"status")))).toList(); }
    @Override public Optional<Long> findActiveUserIdByNormalizedEmail(String normalizedEmail) { return mapper.findActiveUserId(normalizedEmail).stream().findFirst(); }
    @Override public Optional<Long> findActiveMemberUserIdByNormalizedEmail(long workspaceId, String normalizedEmail) { return mapper.findActiveMemberUserId(workspaceId, normalizedEmail).stream().findFirst(); }
    @Override public void activateMember(long workspaceId, long userId) { mapper.activateMember(workspaceId, userId); }
    @Override public void removeMember(long workspaceId, long userId) { mapper.removeMember(workspaceId, userId); }
    private Workspace workspace(Map<String,Object> row) { return new Workspace(number(row,"id"), number(row,"organization_id"), text(row,"name"), text(row,"slug"), true); }
    private long number(Map<String,Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private String text(Map<String,Object> row, String key) { return row.get(key).toString(); }
}
