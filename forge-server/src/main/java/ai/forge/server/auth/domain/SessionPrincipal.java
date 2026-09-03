package ai.forge.server.auth.domain;

import java.io.Serializable;
import java.time.Instant;

public record SessionPrincipal(
        /* 由登录凭据证明且后续用于构建请求身份的用户标识。 */
        long userId,
        /* 本次认证会话首次签发时刻，用于强制执行绝对过期。 */
        Instant issuedAt) implements Serializable {}
