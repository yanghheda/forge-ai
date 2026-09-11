package ai.forge.server.gitlab.application;

import ai.forge.server.gitlab.domain.GitLabConnection;
import ai.forge.server.gitlab.domain.GitRepository;
import java.util.List;
import java.util.Optional;

public interface GitLabConnectionStore {

    GitLabConnection create(
            long organizationId, long createdBy, String name, String baseUrl, EncryptedSecret encryptedSecret);

    List<GitLabConnection> findConnections(long organizationId);

    Optional<GitLabConnection> findConnection(long organizationId, long connectionId);

    Optional<StoredSecret> findCredential(long organizationId, long connectionId);

    Optional<GitLabConnection> rotateCredential(
            long organizationId, long connectionId, long expectedVersion, EncryptedSecret encryptedSecret);

    default void configureWebhookSecret(long organizationId, long connectionId, EncryptedSecret encryptedSecret) {
        throw new UnsupportedOperationException("webhook secret configuration is not implemented");
    }

    void recordTest(long organizationId, long connectionId, boolean successful);

    GitRepository bindRepository(
            long organizationId, long connectionId, RepositoryDto repository);
}
