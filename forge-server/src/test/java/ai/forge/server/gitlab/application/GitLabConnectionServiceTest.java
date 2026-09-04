package ai.forge.server.gitlab.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.authorization.application.PermissionStore;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.gitlab.domain.GitLabConnection;
import ai.forge.server.gitlab.domain.GitRepository;
import ai.forge.server.gitlab.infrastructure.GitLabUrlPolicy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GitLabConnectionServiceTest {

    @Test
    void rotatesTokenAndOnlyPassesPlaintextAcrossTheProviderBoundary() {
        MutableStore store = new MutableStore();
        CapturingProvider provider = new CapturingProvider();
        GitLabConnectionService service = service(store, provider, true);

        GitLabConnection created = service.create(1L, 7L, "Primary", "http://localhost:8080/", "old-token");
        assertThat(created.toString()).doesNotContain("old-token");
        assertThat(store.credential.encrypted().ciphertext()).doesNotContain("old-token");

        GitLabConnection rotated = service.rotate(1L, 7L, created.id(), 0L, "new-token");
        assertThat(rotated.version()).isEqualTo(1L);
        service.test(1L, 7L, created.id());

        assertThat(provider.lastToken).isEqualTo("new-token");
        assertThat(rotated.toString()).doesNotContain("new-token");
        assertThat(store.connection.status()).isEqualTo("ACTIVE");
    }

    @Test
    void deniesCrossWorkspaceBeforeReadingConnectionOrSecret() {
        MutableStore store = new MutableStore();
        GitLabConnectionService service = service(store, new CapturingProvider(), false);

        assertThatThrownBy(() -> service.list(2L, 8L)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(store.readCount).isZero();
    }

    private static GitLabConnectionService service(
            MutableStore store, CapturingProvider provider, boolean authorized) {
        PermissionStore permissionStore = new PermissionStore() {
            @Override
            public Set<String> findWorkspacePermissionSet(long userId, long workspaceId) {
                return authorized ? Set.of("integration.manage") : Set.of();
            }

            @Override
            public Optional<ProjectAccess> findProjectAccess(long userId, long workspaceId, long projectId) {
                return Optional.of(new ProjectAccess(
                        authorized, Set.of("ADMIN"), authorized ? Set.of("project.manage", "repo.read") : Set.of()));
            }

            @Override
            public List<Long> findProjectIdsWithPermission(long userId, long workspaceId, String permission) {
                return List.of();
            }
        };
        SecretService secrets = new SecretService(Base64.getEncoder().encodeToString(new byte[32]), 1);
        return new GitLabConnectionService(
                new PermissionEvaluator(permissionStore),
                store,
                secrets,
                new GitLabUrlPolicy(true, ""),
                provider);
    }

    private static final class CapturingProvider implements SourceControlProvider {
        /* 最近一次调用收到的临时 Token，用于证明轮换后的密文被使用。 */
        private String lastToken;

        @Override
        public ConnectionTestResult test(String baseUrl, String token) {
            lastToken = token;
            return new ConnectionTestResult("42", "admin");
        }

        @Override
        public RepositoryDto getRepository(String baseUrl, String token, String remoteProjectId) {
            lastToken = token;
            return new RepositoryDto(remoteProjectId, "team/repo", "https://example/repo.git", "main");
        }
    }

    private static final class MutableStore implements GitLabConnectionStore {
        /* 当前公开连接快照。 */
        private GitLabConnection connection;
        /* 当前加密凭据事实。 */
        private StoredSecret credential;
        /* 用于确认拒绝发生在持久化访问之前的读取次数。 */
        private int readCount;

        @Override
        public GitLabConnection create(
                long workspaceId, long createdBy, String name, String baseUrl, EncryptedSecret encrypted) {
            credential = new StoredSecret(11L, workspaceId, "GITLAB_TOKEN", encrypted);
            connection = new GitLabConnection(
                    21L, workspaceId, name, baseUrl, encrypted.fingerprint(), "UNVERIFIED", null, 0L);
            return connection;
        }

        @Override
        public List<GitLabConnection> findConnections(long workspaceId) {
            readCount++;
            return connection == null ? List.of() : List.of(connection);
        }

        @Override
        public Optional<GitLabConnection> findConnection(long workspaceId, long connectionId) {
            readCount++;
            return Optional.ofNullable(connection)
                    .filter(value -> value.workspaceId() == workspaceId && value.id() == connectionId);
        }

        @Override
        public Optional<StoredSecret> findCredential(long workspaceId, long connectionId) {
            readCount++;
            return Optional.ofNullable(credential).filter(value -> value.workspaceId() == workspaceId);
        }

        @Override
        public Optional<GitLabConnection> rotateCredential(
                long workspaceId, long connectionId, long expectedVersion, EncryptedSecret encrypted) {
            if (connection.version() != expectedVersion) {
                return Optional.empty();
            }
            credential = new StoredSecret(11L, workspaceId, "GITLAB_TOKEN", encrypted);
            connection = new GitLabConnection(
                    connection.id(), workspaceId, connection.name(), connection.baseUrl(),
                    encrypted.fingerprint(), "UNVERIFIED", null, connection.version() + 1);
            return Optional.of(connection);
        }

        @Override
        public void recordTest(long workspaceId, long connectionId, boolean successful) {
            connection = new GitLabConnection(
                    connection.id(), workspaceId, connection.name(), connection.baseUrl(), connection.tokenFingerprint(),
                    successful ? "ACTIVE" : "ERROR", LocalDateTime.now(), connection.version());
        }

        @Override
        public GitRepository bindRepository(
                long workspaceId, long projectId, long connectionId, RepositoryDto repository) {
            return new GitRepository(
                    31L, workspaceId, projectId, connectionId, repository.remoteProjectId(),
                    repository.pathWithNamespace(), repository.httpUrl(), repository.defaultBranch(),
                    "ACTIVE", LocalDateTime.now(), 0L);
        }
    }
}
