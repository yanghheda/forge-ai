package ai.forge.server.gitlab.application;

import ai.forge.server.gitlab.domain.GitLabConnection;
import ai.forge.server.gitlab.domain.GitRepository;
import java.util.List;
import java.util.Optional;

public interface GitLabConnectionStore {

    GitLabConnection create(
            long workspaceId, long createdBy, String name, String baseUrl, EncryptedSecret encryptedSecret);

    List<GitLabConnection> findConnections(long workspaceId);

    Optional<GitLabConnection> findConnection(long workspaceId, long connectionId);

    Optional<StoredSecret> findCredential(long workspaceId, long connectionId);

    Optional<GitLabConnection> rotateCredential(
            long workspaceId, long connectionId, long expectedVersion, EncryptedSecret encryptedSecret);

    default void configureWebhookSecret(long workspaceId, long connectionId, EncryptedSecret encryptedSecret) {
        throw new UnsupportedOperationException("webhook secret configuration is not implemented");
    }

    void recordTest(long workspaceId, long connectionId, boolean successful);

    GitRepository bindRepository(
            long workspaceId, long projectId, long connectionId, RepositoryDto repository);
}
