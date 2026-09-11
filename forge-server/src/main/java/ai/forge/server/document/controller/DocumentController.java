package ai.forge.server.document.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.auth.domain.AuthContext;
import ai.forge.server.document.application.DocumentService;
import ai.forge.server.document.domain.Document;
import ai.forge.server.document.application.DocumentVersion;
import ai.forge.server.organization.application.OrganizationAccessService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.net.URI;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/documents")
public class DocumentController {

    /* 文档命令、查询和事务边界。 */
    private final DocumentService service;
    /* 从登录身份解析唯一公司作用域。 */
    private final OrganizationAccessService organizations;

    public DocumentController(DocumentService service, OrganizationAccessService organizations) {
        this.service = service;
        this.organizations = organizations;
    }

    @PostMapping
    public ResponseEntity<Document> create(@Valid @RequestBody CreateRequest body, HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        Document document = service.create(
            context.userId(), organizationId(context), body.workItemId(), body.type(), body.title());
        return ResponseEntity.created(URI.create("/api/v1/documents/" + document.id())).body(document);
    }

    @GetMapping("/{documentId}")
    public Document get(
        @PathVariable long documentId, HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        return service.get(context.userId(), organizationId(context), documentId);
    }

    @GetMapping
    public List<Document> list(
        @org.springframework.web.bind.annotation.RequestParam long workItemId, HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        return service.listByWorkItem(context.userId(), organizationId(context), workItemId);
    }

    @GetMapping("/{documentId}/versions")
    public List<DocumentVersion> history(
        @PathVariable long documentId, HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        return service.history(context.userId(), organizationId(context), documentId);
    }

    @PostMapping("/{documentId}/versions")
    public Document save(
        @PathVariable long documentId, @Valid @RequestBody SaveRequest body,
        HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        return service.save(context.userId(), organizationId(context), documentId,
            body.expectedVersion(), body.content());
    }

    @PostMapping("/{documentId}/publish")
    public Document publish(
        @PathVariable long documentId, @Valid @RequestBody PublishRequest body,
        HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        return service.publish(context.userId(), organizationId(context), documentId,
            body.versionId(), body.expectedVersion());
    }

    public record CreateRequest(
        /* 关联的 Requirement；Product 切片要求 PRD 必须归属工作项。 */ @Positive long workItemId,
        /* 文档类型。 */ @NotBlank String type,
        /* 文档标题。 */ @NotBlank String title) {
    }

    public record SaveRequest(
        /* 客户端已读元数据版本。 */ @PositiveOrZero long expectedVersion,
        /* Tiptap ProseMirror 正文。 */ @NotNull JsonNode content) {
    }

    public record PublishRequest(
        /* 要发布的不可变版本。 */ @Positive long versionId,
        /* 客户端已读元数据版本。 */ @PositiveOrZero long expectedVersion) {
    }

    private long organizationId(AuthContext context) {
        return organizations.requireContext(context.userId()).organizationId();
    }
}
