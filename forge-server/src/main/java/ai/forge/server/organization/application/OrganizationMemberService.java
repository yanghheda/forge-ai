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
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class OrganizationMemberService {
    /* 全局管理员与 Owner 可分配的业务角色白名单；含四个领域管理员。 */
    private static final Set<String> ASSIGNABLE_ROLES = Set.of(
            "ADMIN", "PRODUCT", "UX", "DEVELOPER", "QA",
            "PRODUCT_ADMIN", "UX_ADMIN", "DEVELOPER_ADMIN", "QA_ADMIN");
    /* 自助注册允许选择的岗位角色白名单；四个领域管理员只能由全局管理员或 Owner 分配。 */
    private static final Set<String> SELF_REGISTER_ROLES =
            Set.of("ADMIN", "PRODUCT", "UX", "DEVELOPER", "QA");
    /* 游客演示账号固定的规范化登录邮箱；同一实例只保留一个共享游客账号。 */
    private static final String GUEST_EMAIL = "guest@forge.demo";
    /* 游客演示账号在成员与参与人界面展示的名称。 */
    private static final String GUEST_DISPLAY_NAME = "游客";
    /* 游客账号固定持有的四个基础角色与四个领域管理员角色；不含 OWNER 与全局 ADMIN，因此不获得成员治理权限。 */
    private static final List<String> GUEST_ROLES = List.of(
            "PRODUCT", "UX", "DEVELOPER", "QA",
            "PRODUCT_ADMIN", "UX_ADMIN", "DEVELOPER_ADMIN", "QA_ADMIN");
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

    /* 创建或复用共享游客演示账号：跳过人工审核，直接以 ACTIVE 状态持有四个领域的基础角色与领域管理员角色。
       账号使用随机 BCrypt 摘要作为密码，因此只能通过游客模式端点建立会话，无法用密码登录。 */
    public long ensureGuestAccount() {
        List<Long> roleIds = GUEST_ROLES.stream()
                .map(role -> store.findSystemRoleId(role)
                        .orElseThrow(() -> new IllegalStateException("Missing guest role: " + role)))
                .toList();
        return store.ensureActiveAccount(
                GUEST_EMAIL, GUEST_EMAIL, GUEST_DISPLAY_NAME,
                passwordHasher.hash(UUID.randomUUID().toString()), roleIds);
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
        if (!SELF_REGISTER_ROLES.contains(role)) {
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
