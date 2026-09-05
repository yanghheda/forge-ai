package ai.forge.server.gitlab.infrastructure.persistence;

import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.gitlab.application.EncryptedSecret;
import ai.forge.server.gitlab.application.GitLabConnectionStore;
import ai.forge.server.gitlab.application.RepositoryDto;
import ai.forge.server.gitlab.application.StoredSecret;
import ai.forge.server.gitlab.domain.GitLabConnection;
import ai.forge.server.gitlab.domain.GitRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test-unit")
public class MybatisGitLabConnectionStore implements GitLabConnectionStore {

    /* 执行所有显式携带 Workspace 范围的 GitLab MyBatis SQL。 */
    private final GitLabConnectionMapper mapper;

    public MybatisGitLabConnectionStore(GitLabConnectionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public GitLabConnection create(
            long workspaceId, long createdBy, String name, String baseUrl, EncryptedSecret encrypted) {
        mapper.insertSecret(
                workspaceId, encrypted.ciphertext(), encrypted.iv(), encrypted.keyVersion(), encrypted.fingerprint());
        long secretId = mapper.lastInsertId();
        mapper.insertConnection(workspaceId, createdBy, name, baseUrl, secretId);
        long connectionId = mapper.lastInsertId();
        return findConnection(workspaceId, connectionId).orElseThrow();
    }

    @Override
    public List<GitLabConnection> findConnections(long workspaceId) {
        return mapper.findConnections(workspaceId).stream().map(this::connection).toList();
    }

    @Override
    public Optional<GitLabConnection> findConnection(long workspaceId, long connectionId) {
        return mapper.findConnection(workspaceId, connectionId).stream().findFirst().map(this::connection);
    }

    @Override
    public Optional<StoredSecret> findCredential(long workspaceId, long connectionId) {
        return mapper.findCredential(workspaceId, connectionId).stream().findFirst().map(this::secret);
    }

    @Override
    @Transactional
    public Optional<GitLabConnection> rotateCredential(
            long workspaceId, long connectionId, long expectedVersion, EncryptedSecret encrypted) {
        int updated = mapper.rotateCredential(
                workspaceId,
                connectionId,
                expectedVersion,
                encrypted.ciphertext(),
                encrypted.iv(),
                encrypted.keyVersion(),
                encrypted.fingerprint());
        return updated == 1 ? findConnection(workspaceId, connectionId) : Optional.empty();
    }

    @Override
    @Transactional
    public void configureWebhookSecret(long workspaceId, long connectionId, EncryptedSecret encrypted) {
        mapper.insertWebhookSecret(workspaceId, encrypted.ciphertext(), encrypted.iv(),
                encrypted.keyVersion(), encrypted.fingerprint());
        long secretId = mapper.lastInsertId();
        if (mapper.attachWebhookSecret(workspaceId, connectionId, secretId) != 1) {
            throw new IllegalStateException("GitLab connection disappeared while configuring webhook");
        }
    }

    @Override
    @Transactional
    public void recordTest(long workspaceId, long connectionId, boolean successful) {
        mapper.recordTest(workspaceId, connectionId, successful ? "ACTIVE" : "ERROR");
    }

    @Override
    @Transactional
    public GitRepository bindRepository(
            long workspaceId, long projectId, long connectionId, RepositoryDto repository) {
        try {
            mapper.insertRepository(
                    workspaceId,
                    projectId,
                    connectionId,
                    repository.remoteProjectId(),
                    repository.pathWithNamespace(),
                    repository.httpUrl(),
                    repository.defaultBranch());
        } catch (DuplicateKeyException exception) {
            throw new VersionConflictException();
        }
        return mapper.findActiveRepository(workspaceId, projectId).stream()
                .findFirst()
                .map(this::repository)
                .orElseThrow();
    }

    private GitLabConnection connection(Map<String, Object> row) {
        return new GitLabConnection(
                number(row, "id"),
                number(row, "workspace_id"),
                text(row, "name"),
                text(row, "base_url"),
                text(row, "fingerprint"),
                text(row, "status"),
                (LocalDateTime) row.get("last_tested_at"),
                number(row, "version"));
    }

    private StoredSecret secret(Map<String, Object> row) {
        return new StoredSecret(
                number(row, "id"),
                number(row, "workspace_id"),
                text(row, "type"),
                new EncryptedSecret(
                        text(row, "ciphertext"),
                        text(row, "iv"),
                        ((Number) row.get("key_version")).intValue(),
                        text(row, "fingerprint")));
    }

    private GitRepository repository(Map<String, Object> row) {
        return new GitRepository(
                number(row, "id"),
                number(row, "workspace_id"),
                number(row, "project_id"),
                number(row, "connection_id"),
                text(row, "remote_project_id"),
                text(row, "path_with_namespace"),
                text(row, "http_url"),
                row.get("default_branch") == null ? null : text(row, "default_branch"),
                text(row, "status"),
                (LocalDateTime) row.get("last_synced_at"),
                number(row, "version"));
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static String text(Map<String, Object> row, String key) {
        return row.get(key).toString();
    }
}
