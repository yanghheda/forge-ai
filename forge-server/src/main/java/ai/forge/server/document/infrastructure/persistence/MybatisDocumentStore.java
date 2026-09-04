package ai.forge.server.document.infrastructure.persistence;

import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.document.application.DocumentStore;
import ai.forge.server.document.domain.Document;
import ai.forge.server.document.application.DocumentVersion;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test-unit")
public class MybatisDocumentStore implements DocumentStore {
    /* 执行显式范围 SQL。 */ private final DocumentMapper mapper;
    /* 解析持久化正文。 */ private final ObjectMapper objectMapper;

    public MybatisDocumentStore(DocumentMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public boolean requirementExists(long workspaceId, long projectId, long workItemId) {
        return mapper.countRequirement(workspaceId, projectId, workItemId) == 1;
    }

    public Document create(long workspaceId, long projectId, long workItemId, long userId, String type, String title) {
        mapper.insertDocument(workspaceId, projectId, workItemId, userId, type, title);
        return get(workspaceId, projectId, mapper.lastInsertId());
    }

    public Document get(long workspaceId, long projectId, long documentId) {
        return mapper.findDocument(workspaceId, projectId, documentId).stream().findFirst().map(this::document).orElseThrow(ResourceNotFoundException::new);
    }

    public List<Document> listByWorkItem(long workspaceId, long projectId, long workItemId) {
        return mapper.findByWorkItem(workspaceId, projectId, workItemId).stream().map(this::document).toList();
    }

    public List<DocumentVersion> history(long workspaceId, long documentId) {
        return mapper.findVersions(workspaceId, documentId).stream().map(this::version).toList();
    }

    public Document save(long workspaceId, long projectId, long documentId, long userId, long expectedVersion, String content, String plainText, String hash) {
        long no = mapper.nextVersionNo(documentId);
        mapper.insertVersion(workspaceId, documentId, no, content, plainText, hash, userId);
        long versionId = mapper.lastInsertId();
        if (mapper.setCurrentVersion(workspaceId, projectId, documentId, versionId, expectedVersion) != 1)
            throw new VersionConflictException();
        return get(workspaceId, projectId, documentId);
    }

    public Document publish(long workspaceId, long projectId, long documentId, long versionId, long expectedVersion) {
        if (mapper.publish(workspaceId, projectId, documentId, versionId, expectedVersion) != 1)
            throw new VersionConflictException();
        mapper.insertPublishedEvent(workspaceId, projectId, documentId, versionId);
        return get(workspaceId, projectId, documentId);
    }

    private Document document(Map<String, Object> row) {
        return new Document(number(row, "id"), number(row, "workspace_id"), number(row, "project_id"), nullable(row, "work_item_id"), row.get("type").toString(), row.get("title").toString(), row.get("status").toString(), nullable(row, "current_version_id"), number(row, "version"), instant(row.get("created_at")), instant(row.get("updated_at")));
    }

    private DocumentVersion version(Map<String, Object> row) {
        try {
            return new DocumentVersion(number(row, "id"), number(row, "document_id"), number(row, "version_no"), objectMapper.readTree(row.get("content").toString()), row.get("plain_text").toString(), row.get("content_hash").toString(), number(row, "created_by"), instant(row.get("created_at")));
        } catch (Exception exception) {
            throw new IllegalStateException("stored document content is invalid", exception);
        }
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private Long nullable(Map<String, Object> row, String key) {
        return row.get(key) == null ? null : number(row, key);
    }

    private Instant instant(Object value) {
        return value instanceof LocalDateTime local ? local.toInstant(ZoneOffset.UTC) : ((java.sql.Timestamp) value).toInstant();
    }
}
