package ai.forge.server.auth.application;

import ai.forge.server.auth.domain.CurrentUser;
import ai.forge.server.auth.domain.LoginAccount;
import java.time.Duration;
import java.util.Optional;

public interface AuthenticationStore {

    Optional<LoginAccount> findByNormalizedEmail(String normalizedEmail);

    void recordFailure(Long userId, int lockThreshold, Duration lockDuration, String requestId);

    void recordRejected(Long userId, String requestId);

    void recordSuccess(long userId, String requestId);

    void recordLogout(long userId, String requestId);

    Optional<CurrentUser> findCurrentUser(long userId);
}
