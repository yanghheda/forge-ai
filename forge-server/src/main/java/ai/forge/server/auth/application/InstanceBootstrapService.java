package ai.forge.server.auth.application;

import ai.forge.server.auth.domain.BootstrapCommand;
import ai.forge.server.auth.domain.BootstrapResult;
import ai.forge.server.auth.domain.InstanceInitialized;
import ai.forge.server.auth.domain.PasswordPolicy;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class InstanceBootstrapService {

    /* 本地密码转换边界，确保应用服务不依赖具体 BCrypt 实现。 */
    private final PasswordHasher passwordHasher;

    /* 初始化持久化端口，隔离应用规则与 MyBatis-Plus 实现。 */
    private final InstanceBootstrapStore bootstrapStore;

    /* 发布初始化完成事实，供演示环境按需准备默认集成配置。 */
    private final ApplicationEventPublisher events;

    public InstanceBootstrapService(
            PasswordHasher passwordHasher,
            InstanceBootstrapStore bootstrapStore,
            ApplicationEventPublisher events) {
        this.passwordHasher = passwordHasher;
        this.bootstrapStore = bootstrapStore;
        this.events = events;
    }

    public boolean isInitialized() {
        return bootstrapStore.isInitialized();
    }

    public BootstrapResult initialize(BootstrapCommand command) {
        PasswordPolicy.validate(command.password());
        String normalizedEmail = command.adminEmail().trim().toLowerCase(Locale.ROOT);
        String passwordHash = passwordHasher.hash(command.password());

        BootstrapResult result = bootstrapStore.create(command, normalizedEmail, passwordHash);
        /* 无监听者时不影响主流程；演示环境据此准备默认 GitLab 连接与仓库绑定。 */
        events.publishEvent(new InstanceInitialized(result.organizationId()));
        return result;
    }
}
