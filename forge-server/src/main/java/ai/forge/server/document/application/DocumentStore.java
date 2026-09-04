package ai.forge.server.document.application;

import ai.forge.server.document.domain.Document;

import java.util.List;

public interface DocumentStore {
    boolean requirementExists(long workspaceId, long projectId, long workItemId);

    Document create(long workspaceId, long projectId, long workItemId, long userId, String type, String title);

    Document get(long workspaceId, long projectId, long documentId);

    List<Document> listByWorkItem(long workspaceId, long projectId, long workItemId);

    List<DocumentVersion> history(long workspaceId, long documentId);

    Document save(long workspaceId, long projectId, long documentId, long userId, long expectedVersion,
                  String content, String plainText, String hash);

    Document publish(long workspaceId, long projectId, long documentId, long versionId, long expectedVersion);
}
