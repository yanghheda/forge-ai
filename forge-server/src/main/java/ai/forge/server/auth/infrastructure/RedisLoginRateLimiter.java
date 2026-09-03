package ai.forge.server.auth.infrastructure;

import ai.forge.server.auth.application.LoginRateLimiter;
import ai.forge.server.auth.domain.LoginRateLimitedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@Profile("!test-unit")
public class RedisLoginRateLimiter implements LoginRateLimiter {

    /* 计数和首次 TTL 设置必须由 Redis 在一个原子脚本中完成。 */
    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRY = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; return count;",
            Long.class);

    /* 登录限流键与计数的 Redis 操作入口。 */
    private final StringRedisTemplate redisTemplate;

    /* 单个 IP 与规范化邮箱组合在窗口内允许的尝试次数。 */
    private final int maximumAttempts;

    /* 登录尝试计数保留的滚动窗口长度。 */
    private final Duration window;

    public RedisLoginRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${forge.auth.rate-limit-attempts:10}") int maximumAttempts,
            @Value("${forge.auth.rate-limit-window:5m}") Duration window) {
        this.redisTemplate = redisTemplate;
        this.maximumAttempts = maximumAttempts;
        this.window = window;
    }

    @Override
    public void checkAndRecord(String remoteAddress, String normalizedEmail) {
        String material = (remoteAddress == null ? "unknown" : remoteAddress) + "\n" + normalizedEmail;
        String key = "forge:auth:login:" + sha256(material);
        Long attempts = redisTemplate.execute(INCREMENT_WITH_EXPIRY, List.of(key), Long.toString(window.toMillis()));
        if (attempts == null || attempts > maximumAttempts) {
            throw new LoginRateLimitedException();
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
