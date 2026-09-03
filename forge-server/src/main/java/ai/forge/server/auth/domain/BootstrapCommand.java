package ai.forge.server.auth.domain;

public record BootstrapCommand(
        /* 首个实例用户的可联系邮箱，进入服务后会生成规范化唯一键。 */
        String adminEmail,
        /* 首个实例用户在界面中的展示名称。 */
        String adminDisplayName,
        /* 仅供本次 BCrypt 哈希使用的明文密码，禁止进入日志、响应和审计。 */
        String password,
        /* 初始化创建的默认组织展示名称。 */
        String organizationName,
        /* 初始化创建的默认组织稳定路由短名。 */
        String organizationSlug,
        /* 初始化创建的默认工作区展示名称。 */
        String workspaceName,
        /* 初始化创建的默认工作区稳定路由短名。 */
        String workspaceSlug,
        /* 贯穿初始化响应、服务日志与审计记录的请求标识。 */
        String requestId) {}
