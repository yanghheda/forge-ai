package ai.forge.server.organization.infrastructure.persistence;

import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.organization.application.OrganizationStore;
import ai.forge.server.organization.domain.OrganizationContext;
import ai.forge.server.organization.domain.OrganizationMember;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test-unit")
public class MybatisOrganizationStore implements OrganizationStore {
    /* 执行公司成员、状态和角色 SQL 的 Mapper。 */
    private final OrganizationMapper mapper;

    public MybatisOrganizationStore(OrganizationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<OrganizationContext> findContextForUser(long userId) {
        return mapper.findContextForUser(userId).stream().findFirst().map(row -> new OrganizationContext(
                number(row, "organization_id"), text(row, "organization_name"), bool(row, "owner")));
    }

    @Override public Optional<Long> findOrganizationId() { return mapper.findOrganizationId().stream().findFirst(); }
    @Override public boolean userExists(String normalizedEmail) { return mapper.userExists(normalizedEmail); }
    @Override public Optional<Long> findSystemRoleId(String roleCode) { return mapper.findSystemRoleId(roleCode).stream().findFirst(); }

    @Override
    @Transactional
    public long createPendingAccount(
            String email, String normalizedEmail, String displayName, String passwordHash, long roleId) {
        long organizationId = findOrganizationId().orElseThrow(ResourceNotFoundException::new);
        mapper.insertPendingUser(email, normalizedEmail, displayName, passwordHash);
        long userId = mapper.lastInsertId();
        mapper.insertPendingMember(organizationId, userId);
        long memberId = mapper.lastInsertId();
        mapper.insertRole(memberId, roleId);
        return userId;
    }

    @Override public List<OrganizationMember> findMembers(long organizationId) {
        return mapper.findMembers(organizationId).stream().map(this::member).toList();
    }

    @Override public Optional<OrganizationMember> findMember(long organizationId, long userId) {
        return mapper.findMember(organizationId, userId).stream().findFirst().map(this::member);
    }

    @Override
    public boolean hasActiveMember(long organizationId, long userId) {
        return findMember(organizationId, userId).filter(member -> "ACTIVE".equals(member.status())).isPresent();
    }

    @Override
    @Transactional
    public void updateMember(
            long organizationId, long userId, String displayName, String status, List<Long> roleIds,
            long expectedVersion) {
        if (mapper.updateMember(organizationId, userId, status, expectedVersion) != 1) {
            throw new VersionConflictException();
        }
        mapper.updateUser(userId, displayName, status);
        long memberId = mapper.findMemberId(organizationId, userId).stream()
                .findFirst().orElseThrow(ResourceNotFoundException::new);
        mapper.deleteAssignableRoles(memberId);
        roleIds.forEach(roleId -> mapper.insertRole(memberId, roleId));
    }


    private OrganizationMember member(Map<String, Object> row) {
        Object roles = row.get("roles");
        return new OrganizationMember(
                number(row, "user_id"), text(row, "display_name"), text(row, "email"), text(row, "status"),
                roles == null ? List.of() : Arrays.asList(roles.toString().split(",")), instant(row, "last_login_at"),
                number(row, "version"));
    }

    private long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private String text(Map<String, Object> row, String key) { return row.get(key).toString(); }
    private boolean bool(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return Boolean.TRUE.equals(value) || value instanceof Number number && number.intValue() != 0;
    }
    private Instant instant(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : ((LocalDateTime) value).toInstant(ZoneOffset.UTC);
    }
}
