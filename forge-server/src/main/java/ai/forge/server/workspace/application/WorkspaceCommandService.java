package ai.forge.server.workspace.application;

import ai.forge.server.auth.application.PasswordHasher;
import ai.forge.server.auth.domain.PasswordPolicy;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.workspace.domain.MemberEmailConflictException;
import ai.forge.server.workspace.domain.Workspace;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class WorkspaceCommandService {

    /* 允许通过公开注册入口自行取得的业务角色，不包含任何管理角色。 */
    private static final Set<String> SELF_REGISTRATION_ROLES = Set.of("PRODUCT", "UX", "DEVELOPER", "QA");

    /* 检查创建者是否保有初始化阶段已有的 Owner 管理范围。 */
    private final WorkspaceStore workspaceStore;

    /* 验证成员管理请求的当前 Workspace Owner 范围。 */
    private final WorkspaceAccessService workspaceAccessService;

    /* 在写入成员事实前执行最终 RBAC 授权的服务。 */
    private final PermissionEvaluator permissionEvaluator;

    /* 将管理员提交的成员初始密码转换为不可逆摘要。 */
    private final PasswordHasher passwordHasher;

    public WorkspaceCommandService(WorkspaceStore workspaceStore, WorkspaceAccessService workspaceAccessService, PermissionEvaluator permissionEvaluator, PasswordHasher passwordHasher) {
        this.workspaceStore = workspaceStore;
        this.workspaceAccessService = workspaceAccessService;
        this.permissionEvaluator = permissionEvaluator;
        this.passwordHasher = passwordHasher;
    }

    public Workspace create(long userId, String name, String slug) {
        if (!workspaceStore.hasAnyActiveOwnerRole(userId)) {
            throw new ResourceNotFoundException();
        }
        return workspaceStore.createForOwner(userId, name.trim(), slug.trim().toLowerCase(Locale.ROOT));
    }

    public void addMember(long userId, long workspaceId, String email, String roleCode) {
        permissionEvaluator.requireWorkspace(userId, workspaceId, "member.manage");
        long memberUserId = workspaceAccessService.requireActiveUserId(email.trim().toLowerCase(Locale.ROOT));
        long roleId = requireSystemRoleId(roleCode);
        workspaceStore.activateMember(workspaceId, memberUserId);
        workspaceStore.assignWorkspaceRole(workspaceId, memberUserId, roleId);
    }

    public long createMember(long userId, long workspaceId, String email, String displayName, String password, String roleCode) {
        permissionEvaluator.requireWorkspace(userId, workspaceId, "member.manage");
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (workspaceStore.userExistsByNormalizedEmail(normalizedEmail)) {
            throw new MemberEmailConflictException();
        }
        PasswordPolicy.validate(password);
        long roleId = requireSystemRoleId(roleCode);
        String passwordHash = passwordHasher.hash(password);
        return workspaceStore.createMemberAccount(workspaceId, email.trim(), normalizedEmail, displayName.trim(), passwordHash, roleId);
    }

    public long selfRegister(String email, String displayName, String password, String roleCode) {
        String normalizedRole = roleCode.trim().toUpperCase(Locale.ROOT);
        if (!SELF_REGISTRATION_ROLES.contains(normalizedRole)) {
            throw new IllegalArgumentException("Unsupported self-registration role");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (workspaceStore.userExistsByNormalizedEmail(normalizedEmail)) {
            throw new MemberEmailConflictException();
        }
        PasswordPolicy.validate(password);
        long workspaceId = workspaceStore.findDefaultWorkspaceId().orElseThrow(ResourceNotFoundException::new);
        long roleId = requireSystemRoleId(normalizedRole);
        return workspaceStore.createSelfRegisteredAccount(
                workspaceId,
                email.trim(),
                normalizedEmail,
                displayName.trim(),
                passwordHasher.hash(password),
                roleId);
    }

    public void removeMember(long userId, long workspaceId, long memberUserId) {
        permissionEvaluator.requireWorkspace(userId, workspaceId, "member.manage");
        if (userId == memberUserId) {
            throw new IllegalArgumentException("Workspace owner cannot remove their own membership");
        }
        workspaceStore.removeMember(workspaceId, memberUserId);
    }

    private long requireSystemRoleId(String roleCode) {
        return workspaceStore.findSystemRoleIdByCode(roleCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("Unsupported member role"));
    }
}
