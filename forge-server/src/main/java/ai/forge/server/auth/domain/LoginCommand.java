package ai.forge.server.auth.domain;

public record LoginCommand(
        /* 用户提交且只用于本次凭据校验的邮箱地址。 */
        String email,
        /* 用户提交且禁止进入日志、响应或审计的明文密码。 */
        String password,
        /* Servlet 解析后的客户端地址，仅用于生成不可逆限流键。 */
        String remoteAddress,
        /* 贯穿认证状态更新和审计事实的请求标识。 */
        String requestId) {}
