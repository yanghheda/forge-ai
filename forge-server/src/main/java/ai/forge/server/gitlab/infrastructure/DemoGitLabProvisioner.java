package ai.forge.server.gitlab.infrastructure;

import ai.forge.server.auth.domain.InstanceInitialized;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.gitlab.application.EncryptedSecret;
import ai.forge.server.gitlab.application.GitLabConnectionStore;
import ai.forge.server.gitlab.application.RepositoryDto;
import ai.forge.server.gitlab.application.SecretService;
import ai.forge.server.gitlab.application.SourceControlProvider;
import ai.forge.server.gitlab.domain.GitLabConnection;
import ai.forge.server.organization.application.OrganizationStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 演示环境（forge.gitlab.provider=demo）的默认集成准备器。
 *
 * <p>Demo provider 只负责在进程内模拟分支与 MR，开发上下文仍要求数据库中存在 ACTIVE 的仓库连接与凭据。
 * 若不做准备，演示实例创建 Dev Task 后无法“开始处理”，且会被误报为无权访问。
 * 本组件在两个时机幂等准备一份演示连接与仓库绑定：进程启动（覆盖既有实例）与实例首次初始化（覆盖初始化发生在运行期间的情况）。
 */
@Component
@ConditionalOnProperty(name = "forge.gitlab.provider", havingValue = "demo")
public class DemoGitLabProvisioner {

    /* 演示环境固定绑定的仓库路径，Demo provider 为它返回确定性远端快照。 */
    private static final String DEMO_REMOTE_PROJECT = "demo/demo-shop";

    /* 演示连接展示用的根地址；Demo provider 会忽略它，仅作为连接事实保存。 */
    private static final String DEMO_BASE_URL = "https://gitlab.demo.invalid";

    /* 演示连接名称，与人工配置的连接区分开。 */
    private static final String DEMO_CONNECTION_NAME = "Demo GitLab（自动准备）";

    /* Demo provider 不校验 Token，该值只用于生成本地加密凭据，永不回显。 */
    private static final String DEMO_TOKEN = "demo-provisioned-token";

    /* 读取实例默认公司与创建者身份。 */
    private final OrganizationStore organizations;

    /* 写入连接、凭据与仓库绑定事实。 */
    private final GitLabConnectionStore connections;

    /* 把演示 Token 加密为本地凭据。 */
    private final SecretService secrets;

    /* 取得演示仓库快照。 */
    private final SourceControlProvider sourceControl;

    public DemoGitLabProvisioner(
            OrganizationStore organizations,
            GitLabConnectionStore connections,
            SecretService secrets,
            SourceControlProvider sourceControl) {
        this.organizations = organizations;
        this.connections = connections;
        this.secrets = secrets;
        this.sourceControl = sourceControl;
    }

    /* 进程启动后为已初始化的实例补齐演示集成，覆盖重启后仍未绑定的情况。 */
    @EventListener(ApplicationReadyEvent.class)
    public void provisionOnStartup() {
        organizations.findOrganizationId().ifPresent(this::ensure);
    }

    /* 实例在进程运行期间完成初始化时立即准备，避免用户必须重启服务。 */
    @EventListener
    public void provisionAfterInitialization(InstanceInitialized event) {
        ensure(event.organizationId());
    }

    /* 幂等准备：公司已存在任意连接时不做任何改动，以免覆盖人工配置。 */
    void ensure(long organizationId) {
        if (!connections.findConnections(organizationId).isEmpty()) {
            return;
        }
        long ownerUserId = organizations.findOwnerUserId(organizationId)
                .orElseThrow(ResourceNotFoundException::new);
        EncryptedSecret credential = secrets.encrypt(organizationId, "GITLAB_TOKEN", DEMO_TOKEN);
        GitLabConnection connection = connections.create(
                organizationId, ownerUserId, DEMO_CONNECTION_NAME, DEMO_BASE_URL, credential);
        RepositoryDto remote = sourceControl.getRepository(DEMO_BASE_URL, DEMO_TOKEN, DEMO_REMOTE_PROJECT);
        connections.bindRepository(organizationId, connection.id(), remote);
        connections.recordTest(organizationId, connection.id(), true);
    }
}
