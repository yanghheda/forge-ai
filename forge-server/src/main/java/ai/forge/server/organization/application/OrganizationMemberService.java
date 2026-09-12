package ai.forge.server.organization.application;

import ai.forge.server.auth.application.PasswordHasher;
import ai.forge.server.auth.domain.PasswordPolicy;
import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.organization.domain.MemberEmailConflictException;
import ai.forge.server.organization.domain.OrganizationContext;
import ai.forge.server.organization.domain.OrganizationMember;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class OrganizationMemberService {
    /* 自助注册和管理员可分配的业务角色白名单。 */
    private static final Set<String> ASSIGNABLE_ROLES = Set.of(
            "ADMIN", "PRODUCT", "UX", "DEVELOPER", "QA", "RELEASE_APPROVER");
    /* 读取当前会话所属公司的服务。 */
    private final OrganizationAccessService accessService;
    /* 持久化公司成员、状态和角色的端口。 */
    private final OrganizationStore store;
    /* 验证成员治理权限的服务。 */
    private final PermissionEvaluator permissionEvaluator;
    /* 将注册密码转换为不可逆摘要。 */
    private final PasswordHasher passwordHasher;

    public OrganizationMemberService(
            OrganizationAccessService accessService,
            OrganizationStore store,
            PermissionEvaluator permissionEvaluator,
            PasswordHasher passwordHasher) {
        this.accessService = accessService;
        this.store = store;
        this.permissionEvaluator = permissionEvaluator;
        this.passwordHasher = passwordHasher;
    }

    public long selfRegister(String email, String displayName, String password, String roleCode) {
        String role = normalizeRole(roleCode);
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (store.userExists(normalizedEmail)) {
            throw new MemberEmailConflictException();
        }
        PasswordPolicy.validate(password);
        long roleId = store.findSystemRoleId(role).orElseThrow(() -> new IllegalArgumentException("Unsupported role"));
        return store.createPendingAccount(
                email.trim(), normalizedEmail, displayName.trim(), passwordHasher.hash(password), roleId);
    }

    public List<OrganizationMember> list(long actorUserId) {
        OrganizationContext context = accessService.requireContext(actorUserId);
        permissionEvaluator.requireOrganization(actorUserId, context.organizationId(), "member.read");
        return store.findMembers(context.organizationId());
    }

    public OrganizationMember update(
            long actorUserId, long memberUserId, String displayName, String status, List<String> roleCodes,
            long expectedVersion) {
        OrganizationContext context = accessService.requireContext(actorUserId);
        permissionEvaluator.requireOrganization(actorUserId, context.organizationId(), "member.manage");
        if (actorUserId == memberUserId) {
            throw new IllegalArgumentException("Members cannot edit their own account");
        }
        List<Long> roleIds = roleCodes.stream()
                .map(this::normalizeAssignableRole)
                .distinct()
                .map(role -> store.findSystemRoleId(role)
                        .orElseThrow(() -> new IllegalArgumentException("Unsupported role")))
                .toList();
        store.updateMember(context.organizationId(), memberUserId, displayName.trim(), status, roleIds, expectedVersion);
        return store.findMember(context.organizationId(), memberUserId).orElseThrow(ResourceNotFoundException::new);
    }

    private String normalizeRole(String roleCode) {
        String role = roleCode == null ? "" : roleCode.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PRODUCT", "UX", "DEVELOPER", "QA", "RELEASE_APPROVER").contains(role)) {
            throw new IllegalArgumentException("Unsupported role");
        }
        return role;
    }

    private String normalizeAssignableRole(String roleCode) {
        String role = roleCode == null ? "" : roleCode.trim().toUpperCase(Locale.ROOT);
        if (!ASSIGNABLE_ROLES.contains(role)) {
            throw new IllegalArgumentException("Unsupported role");
        }
        return role;
    }
}
