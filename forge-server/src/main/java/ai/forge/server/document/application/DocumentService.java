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

    /* 具有显式公司作用域条件的 MyBatis SQL。 */
    private final DocumentStore store;

    public DocumentService(
        PermissionEvaluator permissions, DocumentStore store) {
        this.permissions = permissions;
        this.store = store;
    }

    @Transactional
    public Document create(
        long userId, long organizationId, long workItemId, String type, String title) {
        permissions.requireOrganization(userId, organizationId, "document.create");
        if (!SUPPORTED_TYPES.contains(type) || title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("only nonblank Product or UX documents are supported");
        }
        if (!store.requirementExists(organizationId, workItemId)) {
            throw new ResourceNotFoundException();
        }
        return store.create(organizationId, workItemId, userId, type, title.trim());
    }

    public Document get(long userId, long organizationId, long documentId) {
        permissions.requireOrganization(userId, organizationId, "document.read");
        return store.get(organizationId, documentId);
    }

    public List<Document> listByWorkItem(
        long userId, long organizationId, long workItemId) {
        permissions.requireOrganization(userId, organizationId, "document.read");
        if (!store.requirementExists(organizationId, workItemId)) {
            throw new ResourceNotFoundException();
        }
        return store.listByWorkItem(organizationId, workItemId);
    }

    public List<DocumentVersion> history(long userId, long organizationId, long documentId) {
        get(userId, organizationId, documentId);
        return store.history(organizationId, documentId);
    }

    @Transactional
    public Document save(long userId, long organizationId, long documentId, long expectedVersion, JsonNode content) {
        permissions.requireOrganization(userId, organizationId, "document.edit");
        store.get(organizationId, documentId);
        DocumentContent normalized = DocumentContent.from(content);
        return store.save(organizationId, documentId, userId, expectedVersion,
            normalized.contentJson(), normalized.plainText(), normalized.hash());
    }

    @Transactional
    public Document publish(long userId, long organizationId, long documentId, long versionId, long expectedVersion) {
        permissions.requireOrganization(userId, organizationId, "document.publish");
        store.get(organizationId, documentId);
        return store.publish(organizationId, documentId, versionId, expectedVersion);
    }
}
