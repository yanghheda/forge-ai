package ai.forge.server.document.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.document.domain.Document;
import ai.forge.server.document.domain.DocumentContent;
import ai.forge.server.document.domain.DocumentVersion;
import ai.forge.server.document.infrastructure.persistence.DocumentMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test-unit")
public class DocumentService {
    /* 项目范围授权的最终服务端判断。 */
    private final PermissionEvaluator permissions;

    /* 具有显式 workspace 与 project 范围条件的 MyBatis SQL。 */
    private final DocumentMapper mapper;

    /* 解析持久化 JSON 为 API 返回正文。 */
    private final ObjectMapper objectMapper;

    public DocumentService(
            PermissionEvaluator permissions, DocumentMapper mapper, ObjectMapper objectMapper) {
        this.permissions = permissions;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Document create(long userId, long workspaceId, long projectId, String type, String title) {
        permissions.requireProject(userId, workspaceId, projectId, "document.create");
        if (!"PRD".equals(type) || title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("only nonblank PRD documents are supported");
        }
        mapper.insertDocument(workspaceId, projectId, userId, type, title.trim());
        return document(workspaceId, projectId, mapper.lastInsertId());
    }
    public Document get(long userId, long workspaceId, long projectId, long documentId) {
        permissions.requireProject(userId, workspaceId, projectId, "document.read");
        return document(workspaceId, projectId, documentId);
    }

    public List<DocumentVersion> history(long userId, long workspaceId, long projectId, long documentId) {
        get(userId, workspaceId, projectId, documentId);
        return mapper.findVersions(workspaceId, documentId).stream().map(this::version).toList();
    }
    @Transactional
    public Document save(long userId, long workspaceId, long projectId, long documentId, long expectedVersion, JsonNode content) {
        permissions.requireProject(userId, workspaceId, projectId, "document.edit");
        document(workspaceId, projectId, documentId);
        DocumentContent normalized = DocumentContent.from(content);
        long versionNo = mapper.nextVersionNo(documentId);
        mapper.insertVersion(
                workspaceId,
                documentId,
                versionNo,
                normalized.contentJson(),
                normalized.plainText(),
                normalized.hash(),
                userId);
        long versionId = mapper.lastInsertId();
        if (mapper.setCurrentVersion(workspaceId, projectId, documentId, versionId, expectedVersion) != 1) {
            throw new VersionConflictException();
        }
        return document(workspaceId, projectId, documentId);
    }
    @Transactional
    public Document publish(long userId, long workspaceId, long projectId, long documentId, long versionId, long expectedVersion) {
        permissions.requireProject(userId, workspaceId, projectId, "document.publish");
        document(workspaceId, projectId, documentId);
        if (mapper.publish(workspaceId, projectId, documentId, versionId, expectedVersion) != 1) {
            throw new VersionConflictException();
        }
        mapper.insertPublishedEvent(workspaceId, projectId, documentId, versionId);
        return document(workspaceId, projectId, documentId);
    }
    private Document document(long workspaceId, long projectId, long documentId) {
        return mapper.findDocument(workspaceId, projectId, documentId).stream()
                .findFirst()
                .map(this::document)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private Document document(Map<String, Object> row) {
        return new Document(
                ((Number) row.get("id")).longValue(), ((Number) row.get("workspace_id")).longValue(),
                ((Number) row.get("project_id")).longValue(), (String) row.get("type"),
                (String) row.get("title"), (String) row.get("status"),
                row.get("current_version_id") == null ? null : ((Number) row.get("current_version_id")).longValue(),
                ((Number) row.get("version")).longValue(),
                ((java.sql.Timestamp) row.get("created_at")).toInstant(),
                ((java.sql.Timestamp) row.get("updated_at")).toInstant());
    }

    private DocumentVersion version(Map<String, Object> row) {
        try {
            return new DocumentVersion(
                    ((Number) row.get("id")).longValue(), ((Number) row.get("document_id")).longValue(),
                    ((Number) row.get("version_no")).longValue(), objectMapper.readTree((String) row.get("content")),
                    (String) row.get("plain_text"), (String) row.get("content_hash"),
                    ((Number) row.get("created_by")).longValue(),
                    ((java.sql.Timestamp) row.get("created_at")).toInstant());
        } catch (Exception exception) {
            throw new IllegalStateException("stored document content is invalid", exception);
        }
    }
}
