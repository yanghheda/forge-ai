package ai.forge.server.gitlab.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.gitlab.domain.GitLabConnection;
import ai.forge.server.gitlab.domain.GitRepository;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class GitLabConnectionService {

    /* 执行工作区与项目最终授权的服务端权限边界。 */
    private final PermissionEvaluator permissions;

    /* 持久化连接、加密凭据和仓库绑定的租户范围端口。 */
    private final GitLabConnectionStore store;

    /* 唯一允许在服务端临时获得 Token 明文的密码服务。 */
    private final SecretService secrets;

    /* 在保存前和每次远端请求前验证 Base URL 的 SSRF 策略。 */
    private final GitLabBaseUrlPolicy urlPolicy;

    /* 隔离 GitLab 特有 HTTP 与标准应用 DTO 的只读 SPI。 */
    private final SourceControlProvider sourceControl;

    public GitLabConnectionService(
            PermissionEvaluator permissions,
            GitLabConnectionStore store,
            SecretService secrets,
            GitLabBaseUrlPolicy urlPolicy,
            SourceControlProvider sourceControl) {
        this.permissions = permissions;
        this.store = store;
        this.secrets = secrets;
        this.urlPolicy = urlPolicy;
        this.sourceControl = sourceControl;
    }

    public GitLabConnection create(long userId, long organizationId, String name, String baseUrl, String token) {
        permissions.requireOrganization(userId, organizationId, "integration.manage");
        String normalizedBaseUrl = urlPolicy.validate(baseUrl).toString();
        EncryptedSecret encrypted = secrets.encrypt(organizationId, "GITLAB_TOKEN", token);
        return store.create(organizationId, userId, name.trim(), normalizedBaseUrl, encrypted);
    }

    public List<GitLabConnection> list(long userId, long organizationId) {
        permissions.requireOrganization(userId, organizationId, "integration.manage");
        return store.findConnections(organizationId);
    }

    public GitLabConnection rotate(
            long userId, long organizationId, long connectionId, long expectedVersion, String token) {
        permissions.requireOrganization(userId, organizationId, "integration.manage");
        store.findConnection(organizationId, connectionId).orElseThrow(ResourceNotFoundException::new);
        EncryptedSecret encrypted = secrets.encrypt(organizationId, "GITLAB_TOKEN", token);
        return store.rotateCredential(organizationId, connectionId, expectedVersion, encrypted)
                .orElseThrow(VersionConflictException::new);
    }

    public ConnectionTestResult test(long userId, long organizationId, long connectionId) {
        permissions.requireOrganization(userId, organizationId, "integration.manage");
        GitLabConnection connection = store.findConnection(organizationId, connectionId)
                .orElseThrow(ResourceNotFoundException::new);
        String token = decryptCredential(organizationId, connectionId);
        try {
            ConnectionTestResult result = sourceControl.test(connection.baseUrl(), token);
            store.recordTest(organizationId, connectionId, true);
            return result;
        } catch (RuntimeException exception) {
            store.recordTest(organizationId, connectionId, false);
            throw exception;
        }
    }

    public void configureWebhookSecret(long userId, long organizationId, long connectionId, String secret) {
        permissions.requireOrganization(userId, organizationId, "integration.manage");
        store.findConnection(organizationId, connectionId).orElseThrow(ResourceNotFoundException::new);
        EncryptedSecret encrypted = secrets.encrypt(organizationId, "GITLAB_WEBHOOK_SECRET", secret);
        store.configureWebhookSecret(organizationId, connectionId, encrypted);
    }

    public GitRepository bindRepository(
            long userId, long organizationId, long connectionId, String remoteProjectId) {
        permissions.requireOrganization(userId, organizationId, "integration.manage");
        permissions.requireOrganization(userId, organizationId, "repo.read");
        GitLabConnection connection = store.findConnection(organizationId, connectionId)
                .orElseThrow(ResourceNotFoundException::new);
        String token = decryptCredential(organizationId, connectionId);
        RepositoryDto remote = sourceControl.getRepository(connection.baseUrl(), token, remoteProjectId);
        return store.bindRepository(organizationId, connectionId, remote);
    }

    private String decryptCredential(long organizationId, long connectionId) {
        StoredSecret credential = store.findCredential(organizationId, connectionId)
                .orElseThrow(ResourceNotFoundException::new);
        return secrets.decrypt(organizationId, credential.type(), credential.encrypted());
    }
}
