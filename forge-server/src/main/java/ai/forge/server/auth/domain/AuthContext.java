package ai.forge.server.auth.domain;

public record AuthContext(
        /* 当前请求经服务端 Session 证明的用户标识。 */
        long userId) {}
