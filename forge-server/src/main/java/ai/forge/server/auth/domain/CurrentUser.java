package ai.forge.server.auth.domain;

public record CurrentUser(
        /* 当前登录用户的稳定业务标识。 */
        long id,
        /* 当前用户用于展示与联系的电子邮箱。 */
        String email,
        /* 当前用户在界面显示的名称。 */
        String displayName,
        /* 当前用户所属且状态有效的公司。 */
        OrganizationAccess organization) {

    public record OrganizationAccess(
            /* 当前用户所属公司标识。 */
            long id,
            /* 公司的稳定路由短名。 */
            String slug,
            /* 公司的界面展示名称。 */
            String name,
            /* 当前公司成员拥有的角色代码。 */
            java.util.List<String> roles) {}
}
