package ai.forge.server.document.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.auth.domain.AuthContext;
import ai.forge.server.document.application.DocumentService;
import ai.forge.server.document.domain.Document;
import ai.forge.server.document.domain.DocumentVersion;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/documents")
public class DocumentController {

    /* 文档命令、查询和事务边界。 */
    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Document> create(@Valid @RequestBody CreateRequest body, HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        Document document = service.create(
                context.userId(), body.workspaceId(), body.projectId(), body.type(), body.title());
        return ResponseEntity.created(URI.create("/api/v1/documents/" + document.id())).body(document);
    }

    @GetMapping("/{documentId}")
    public Document get(
            @PathVariable long documentId,
            @RequestParam long workspaceId,
            @RequestParam long projectId,
            HttpServletRequest request) {
        return service.get(AuthController.requireContext(request).userId(), workspaceId, projectId, documentId);
    }

    @GetMapping("/{documentId}/versions")
    public List<DocumentVersion> history(
            @PathVariable long documentId,
            @RequestParam long workspaceId,
            @RequestParam long projectId,
            HttpServletRequest request) {
        return service.history(AuthController.requireContext(request).userId(), workspaceId, projectId, documentId);
    }

    @PostMapping("/{documentId}/versions")
    public Document save(
            @PathVariable long documentId,
            @RequestParam long workspaceId,
            @RequestParam long projectId,
            @Valid @RequestBody SaveRequest body,
            HttpServletRequest request) {
        return service.save(AuthController.requireContext(request).userId(), workspaceId, projectId, documentId,
                body.expectedVersion(), body.content());
    }

    @PostMapping("/{documentId}/publish")
    public Document publish(
            @PathVariable long documentId,
            @RequestParam long workspaceId,
            @RequestParam long projectId,
            @Valid @RequestBody PublishRequest body,
            HttpServletRequest request) {
        return service.publish(AuthController.requireContext(request).userId(), workspaceId, projectId, documentId,
                body.versionId(), body.expectedVersion());
    }

    public record CreateRequest(
            /* 工作区范围。 */ @Positive long workspaceId,
            /* 项目范围。 */ @Positive long projectId,
            /* 文档类型。 */ @NotBlank String type,
            /* 文档标题。 */ @NotBlank String title) {}

    public record SaveRequest(
            /* 客户端已读元数据版本。 */ @PositiveOrZero long expectedVersion,
            /* Tiptap ProseMirror 正文。 */ @NotNull JsonNode content) {}

    public record PublishRequest(
            /* 要发布的不可变版本。 */ @Positive long versionId,
            /* 客户端已读元数据版本。 */ @PositiveOrZero long expectedVersion) {}
}
