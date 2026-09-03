package ai.forge.server.auth.domain;

public record AuthenticatedUser(
        /* 已通过本地密码验证的用户标识。 */
        long userId) {}
