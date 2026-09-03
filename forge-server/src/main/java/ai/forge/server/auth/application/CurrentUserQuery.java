package ai.forge.server.auth.application;

import ai.forge.server.auth.domain.CurrentUser;
import ai.forge.server.auth.domain.UnauthenticatedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class CurrentUserQuery {

    /* 当前用户及其有效租户范围的 MySQL 查询端口。 */
    private final AuthenticationStore authenticationStore;

    public CurrentUserQuery(AuthenticationStore authenticationStore) {
        this.authenticationStore = authenticationStore;
    }

    public CurrentUser get(long userId) {
        return authenticationStore.findCurrentUser(userId).orElseThrow(UnauthenticatedException::new);
    }
}
