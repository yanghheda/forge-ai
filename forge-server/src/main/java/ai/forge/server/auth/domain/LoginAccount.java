package ai.forge.server.auth.domain;

public record LoginAccount(
        /* 用户在实例内的稳定业务标识。 */
        long userId,
        /* BCrypt 摘要，仅允许在认证边界内参与校验。 */
        String passwordHash,
        /* 用户生命周期状态，只有 ACTIVE 可建立会话。 */
        String status,
        /* 由 MySQL UTC 时钟直接判断的当前锁定状态。 */
        boolean locked) {}
