package ai.forge.server.document.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.document.domain.Document;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test-unit")
public class DocumentService {
    /* 当前阶段开放给 Requirement 的产品、UX 与技术设计文档类型。 */
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "PRD", "UX_SPEC", "PROTOTYPE_SPEC", "DESIGN_GUIDE", "TECH_DESIGN");
    /* 项目范围授权的最终服务端判断。 */
    private final PermissionEvaluator permissions;

    /* 具有显式 workspace 与 project 范围条件的 MyBatis SQL。 */
    private final DocumentStore store;

    public DocumentService(
        PermissionEvaluator permissions, DocumentStore store) {
        this.permissions = permissions;
        this.store = store;
    }

    @Transactional
    public Document create(
        long userId, long workspaceId, long projectId, long workItemId, String type, String title) {
        permissions.requireProject(userId, workspaceId, projectId, "document.create");
        if (!SUPPORTED_TYPES.contains(type) || title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("only nonblank Product or UX documents are supported");
        }
        if (!store.requirementExists(workspaceId, projectId, workItemId)) {
            throw new ResourceNotFoundException();
        }
        return store.create(workspaceId, projectId, workItemId, userId, type, title.trim());
    }

    public Document get(long userId, long workspaceId, long projectId, long documentId) {
        permissions.requireProject(userId, workspaceId, projectId, "document.read");
        return store.get(workspaceId, projectId, documentId);
    }

    public List<Document> listByWorkItem(
        long userId, long workspaceId, long projectId, long workItemId) {
        permissions.requireProject(userId, workspaceId, projectId, "document.read");
        if (!store.requirementExists(workspaceId, projectId, workItemId)) {
            throw new ResourceNotFoundException();
        }
        return store.listByWorkItem(workspaceId, projectId, workItemId);
    }

    public List<DocumentVersion> history(long userId, long workspaceId, long projectId, long documentId) {
        get(userId, workspaceId, projectId, documentId);
        return store.history(workspaceId, documentId);
    }

    @Transactional
    public Document save(long userId, long workspaceId, long projectId, long documentId, long expectedVersion, JsonNode content) {
        permissions.requireProject(userId, workspaceId, projectId, "document.edit");
        store.get(workspaceId, projectId, documentId);
        DocumentContent normalized = DocumentContent.from(content);
        return store.save(workspaceId, projectId, documentId, userId, expectedVersion,
            normalized.contentJson(), normalized.plainText(), normalized.hash());
    }

    @Transactional
    public Document publish(long userId, long workspaceId, long projectId, long documentId, long versionId, long expectedVersion) {
        permissions.requireProject(userId, workspaceId, projectId, "document.publish");
        store.get(workspaceId, projectId, documentId);
        return store.publish(workspaceId, projectId, documentId, versionId, expectedVersion);
    }
}
