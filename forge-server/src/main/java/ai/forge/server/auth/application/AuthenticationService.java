package ai.forge.server.auth.application;

import ai.forge.server.auth.domain.AuthenticatedUser;
import ai.forge.server.auth.domain.InvalidCredentialsException;
import ai.forge.server.auth.domain.LoginAccount;
import ai.forge.server.auth.domain.LoginCommand;
import ai.forge.server.auth.domain.LoginRateLimitedException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class AuthenticationService {

    /* 不存在用户时使用的合法 BCrypt 摘要，降低邮箱枚举的时序差异。 */
    private static final String DUMMY_PASSWORD_HASH = "$2b$12$K3ssBIV7Jx1VHUzv8P4XfOmUgA.Ah7xQJ2dHkQBk4oMWSVDTDK/cW";

    /* 本地密码验证边界。 */
    private final PasswordHasher passwordHasher;

    /* 认证事实和审计的 MySQL 持久化端口。 */
    private final AuthenticationStore authenticationStore;

    /* 基于 Redis 的短期登录尝试限流端口。 */
    private final LoginRateLimiter rateLimiter;

    /* 账户连续失败后进入时间锁的阈值。 */
    private final int failedLoginThreshold;

    /* 达到账户失败阈值后的锁定时长。 */
    private final Duration accountLockDuration;

    @Autowired
    public AuthenticationService(
            PasswordHasher passwordHasher,
            AuthenticationStore authenticationStore,
            LoginRateLimiter rateLimiter,
            @Value("${forge.auth.failed-login-threshold:5}") int failedLoginThreshold,
            @Value("${forge.auth.account-lock-duration:15m}") Duration accountLockDuration) {
        this.passwordHasher = passwordHasher;
        this.authenticationStore = authenticationStore;
        this.rateLimiter = rateLimiter;
        this.failedLoginThreshold = failedLoginThreshold;
        this.accountLockDuration = accountLockDuration;
    }

    public AuthenticatedUser authenticate(LoginCommand command) {
        String normalizedEmail = command.email().trim().toLowerCase(Locale.ROOT);
        try {
            rateLimiter.checkAndRecord(command.remoteAddress(), normalizedEmail);
        } catch (LoginRateLimitedException exception) {
            authenticationStore.recordRejected(null, command.requestId());
            throw exception;
        }
        Optional<LoginAccount> possibleAccount = authenticationStore.findByNormalizedEmail(normalizedEmail);
        LoginAccount account = possibleAccount.orElse(null);
        boolean passwordWithinBcryptLimit = command.password().getBytes(StandardCharsets.UTF_8).length <= 72;
        boolean passwordMatches = passwordHasher.matches(
                passwordWithinBcryptLimit ? command.password() : "over-limit-password",
                account == null || !passwordWithinBcryptLimit ? DUMMY_PASSWORD_HASH : account.passwordHash());
        boolean accountAvailable = account != null
                && "ACTIVE".equals(account.status())
                && !account.locked();

        if (account != null && account.locked()) {
            authenticationStore.recordRejected(account.userId(), command.requestId());
            throw new InvalidCredentialsException();
        }
        if (!passwordWithinBcryptLimit || !passwordMatches || !accountAvailable) {
            authenticationStore.recordFailure(account == null ? null : account.userId(),
                    failedLoginThreshold, accountLockDuration, command.requestId());
            throw new InvalidCredentialsException();
        }

        authenticationStore.recordSuccess(account.userId(), command.requestId());
        return new AuthenticatedUser(account.userId());
    }

    public void recordLogout(long userId, String requestId) {
        authenticationStore.recordLogout(userId, requestId);
    }
}
