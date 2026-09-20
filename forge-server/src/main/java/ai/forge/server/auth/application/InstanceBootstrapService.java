package ai.forge.server.auth.application;

import ai.forge.server.auth.domain.BootstrapCommand;
import ai.forge.server.auth.domain.BootstrapResult;
import ai.forge.server.auth.domain.PasswordPolicy;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class InstanceBootstrapService {

    /* 本地密码转换边界，确保应用服务不依赖具体 BCrypt 实现。 */
    private final PasswordHasher passwordHasher;

    /* 初始化持久化端口，隔离应用规则与 MyBatis-Plus 实现。 */
    private final InstanceBootstrapStore bootstrapStore;

    public InstanceBootstrapService(PasswordHasher passwordHasher, InstanceBootstrapStore bootstrapStore) {
        this.passwordHasher = passwordHasher;
        this.bootstrapStore = bootstrapStore;
    }

    public boolean isInitialized() {
        return bootstrapStore.isInitialized();
    }

    public BootstrapResult initialize(BootstrapCommand command) {
        PasswordPolicy.validate(command.password());
        String normalizedEmail = command.adminEmail().trim().toLowerCase(Locale.ROOT);
        String passwordHash = passwordHasher.hash(command.password());

        return bootstrapStore.create(command, normalizedEmail, passwordHash);
    }
}
