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

    public GitLabConnection create(long userId, long workspaceId, String name, String baseUrl, String token) {
        permissions.requireWorkspace(userId, workspaceId, "integration.manage");
        String normalizedBaseUrl = urlPolicy.validate(baseUrl).toString();
        EncryptedSecret encrypted = secrets.encrypt(workspaceId, "GITLAB_TOKEN", token);
        return store.create(workspaceId, userId, name.trim(), normalizedBaseUrl, encrypted);
    }

    public List<GitLabConnection> list(long userId, long workspaceId) {
        permissions.requireWorkspace(userId, workspaceId, "integration.manage");
        return store.findConnections(workspaceId);
    }

    public GitLabConnection rotate(
            long userId, long workspaceId, long connectionId, long expectedVersion, String token) {
        permissions.requireWorkspace(userId, workspaceId, "integration.manage");
        store.findConnection(workspaceId, connectionId).orElseThrow(ResourceNotFoundException::new);
        EncryptedSecret encrypted = secrets.encrypt(workspaceId, "GITLAB_TOKEN", token);
        return store.rotateCredential(workspaceId, connectionId, expectedVersion, encrypted)
                .orElseThrow(VersionConflictException::new);
    }

    public ConnectionTestResult test(long userId, long workspaceId, long connectionId) {
        permissions.requireWorkspace(userId, workspaceId, "integration.manage");
        GitLabConnection connection = store.findConnection(workspaceId, connectionId)
                .orElseThrow(ResourceNotFoundException::new);
        String token = decryptCredential(workspaceId, connectionId);
        try {
            ConnectionTestResult result = sourceControl.test(connection.baseUrl(), token);
            store.recordTest(workspaceId, connectionId, true);
            return result;
        } catch (RuntimeException exception) {
            store.recordTest(workspaceId, connectionId, false);
            throw exception;
        }
    }

    public void configureWebhookSecret(long userId, long workspaceId, long connectionId, String secret) {
        permissions.requireWorkspace(userId, workspaceId, "integration.manage");
        store.findConnection(workspaceId, connectionId).orElseThrow(ResourceNotFoundException::new);
        EncryptedSecret encrypted = secrets.encrypt(workspaceId, "GITLAB_WEBHOOK_SECRET", secret);
        store.configureWebhookSecret(workspaceId, connectionId, encrypted);
    }

    public GitRepository bindRepository(
            long userId, long workspaceId, long projectId, long connectionId, String remoteProjectId) {
        permissions.requireProject(userId, workspaceId, projectId, "project.manage");
        permissions.requireProject(userId, workspaceId, projectId, "repo.read");
        GitLabConnection connection = store.findConnection(workspaceId, connectionId)
                .orElseThrow(ResourceNotFoundException::new);
        String token = decryptCredential(workspaceId, connectionId);
        RepositoryDto remote = sourceControl.getRepository(connection.baseUrl(), token, remoteProjectId);
        return store.bindRepository(workspaceId, projectId, connectionId, remote);
    }

    private String decryptCredential(long workspaceId, long connectionId) {
        StoredSecret credential = store.findCredential(workspaceId, connectionId)
                .orElseThrow(ResourceNotFoundException::new);
        return secrets.decrypt(workspaceId, credential.type(), credential.encrypted());
    }
}
