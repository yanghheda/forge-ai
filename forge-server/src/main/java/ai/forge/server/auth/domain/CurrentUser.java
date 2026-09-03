package ai.forge.server.auth.domain;

import java.util.List;

public record CurrentUser(
        /* 当前登录用户的稳定业务标识。 */
        long id,
        /* 当前用户用于展示与联系的电子邮箱。 */
        String email,
        /* 当前用户在界面显示的名称。 */
        String displayName,
        /* 从 MySQL 实时解析出的有效 Workspace 范围。 */
        List<WorkspaceAccess> workspaces) {

    public record WorkspaceAccess(
            /* 当前用户可访问的 Workspace 标识。 */
            long id,
            /* Workspace 的稳定路由短名。 */
            String slug,
            /* Workspace 的界面展示名称。 */
            String name,
            /* 当前成员在 Workspace 范围已拥有的角色代码。 */
            List<String> roles) {}
}
