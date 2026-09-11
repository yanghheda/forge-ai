package ai.forge.server.document.application;

import ai.forge.server.document.domain.Document;

import java.util.List;

public interface DocumentStore {
    boolean requirementExists(long organizationId, long workItemId);

    Document create(long organizationId, long workItemId, long userId, String type, String title);

    Document get(long organizationId, long documentId);

    List<Document> listByWorkItem(long organizationId, long workItemId);

    List<DocumentVersion> history(long organizationId, long documentId);

    Document save(long organizationId, long documentId, long userId, long expectedVersion,
                  String content, String plainText, String hash);

    Document publish(long organizationId, long documentId, long versionId, long expectedVersion);
}
