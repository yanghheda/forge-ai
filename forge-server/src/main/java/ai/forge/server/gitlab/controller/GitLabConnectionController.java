package ai.forge.server.gitlab.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.gitlab.application.ConnectionTestResult;
import ai.forge.server.gitlab.application.GitLabConnectionService;
import ai.forge.server.gitlab.domain.GitLabConnection;
import ai.forge.server.gitlab.domain.GitRepository;
import ai.forge.server.organization.application.OrganizationAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/gitlab")
@Tag(name = "GitLab", description = "GitLab 连接、凭据轮换、连接测试与仓库绑定")
public class GitLabConnectionController {

    /* 编排权限、Secret、SSRF 策略、GitLab 只读 SPI 和短事务写入。 */
    private final GitLabConnectionService service;
    /* 从登录身份解析唯一公司作用域。 */
    private final OrganizationAccessService organizations;

    public GitLabConnectionController(GitLabConnectionService service, OrganizationAccessService organizations) {
        this.service = service;
        this.organizations = organizations;
    }

    @PostMapping("/connections")
    @Operation(summary = "保存 GitLab 连接", description = "权限：integration.manage；响应永不包含 Token。")
    public ResponseEntity<GitLabConnection> create(
            @Valid @RequestBody CreateConnectionRequest body, HttpServletRequest request) {
        GitLabConnection created = service.create(
                AuthController.requireContext(request).userId(),
                organizationId(request),
                body.name(),
                body.baseUrl(),
                body.token());
        return ResponseEntity.created(URI.create("/api/v1/gitlab/connections/" + created.id())).body(created);
    }

    @GetMapping("/connections")
    @Operation(summary = "列出 GitLab 连接", description = "权限：integration.manage；仅返回 Token 指纹。")
    public List<GitLabConnection> list(HttpServletRequest request) {
        return service.list(AuthController.requireContext(request).userId(), organizationId(request));
    }

    @PatchMapping("/connections/{connectionId}/token")
    @Operation(summary = "轮换 GitLab Token", description = "权限：integration.manage；使用 expectedVersion 乐观锁。")
    public GitLabConnection rotate(
            @PathVariable long connectionId,
            @Valid @RequestBody RotateTokenRequest body,
            HttpServletRequest request) {
        return service.rotate(
                AuthController.requireContext(request).userId(),
                organizationId(request),
                connectionId,
                body.expectedVersion(),
                body.token());
    }

    @PostMapping("/connections/{connectionId}/test")
    @Operation(summary = "测试 GitLab 连接", description = "权限：integration.manage；远端调用不在数据库事务中。")
    public ConnectionTestResult test(
            @PathVariable long connectionId, HttpServletRequest request) {
        return service.test(AuthController.requireContext(request).userId(), organizationId(request), connectionId);
    }

    @PostMapping("/connections/{connectionId}/webhook-secret")
    @Operation(summary = "配置 Webhook Secret", description = "权限：integration.manage；响应永不返回 Secret。")
    public ResponseEntity<Void> configureWebhookSecret(
            @PathVariable long connectionId,
            @Valid @RequestBody ConfigureWebhookSecretRequest body,
            HttpServletRequest request) {
        service.configureWebhookSecret(AuthController.requireContext(request).userId(), organizationId(request),
                connectionId, body.secret());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/repositories/bind")
    @Operation(summary = "绑定 GitLab 仓库", description = "权限：integration.manage 与 repo.read；先读远端再短事务保存快照。")
    public GitRepository bind(@Valid @RequestBody BindRepositoryRequest body, HttpServletRequest request) {
        return service.bindRepository(
                AuthController.requireContext(request).userId(),
                organizationId(request),
                body.connectionId(),
                body.remoteProjectId());
    }

    public record CreateConnectionRequest(
            /* 公司内可识别且唯一的连接名称。 */ @NotBlank @Size(max = 120) String name,
            /* GitLab.com 或私有 GitLab 的根地址。 */ @NotBlank @Size(max = 500) String baseUrl,
            /* 只在本次请求内存在且不会进入响应或日志的访问 Token。 */
            @NotBlank @Size(max = 2000) String token) {}

    public record RotateTokenRequest(
            /* 客户端最后读取的连接版本。 */ @PositiveOrZero long expectedVersion,
            /* 替换旧密文的新 GitLab Token。 */ @NotBlank @Size(max = 2000) String token) {}

    public record BindRepositoryRequest(
            /* 读取远端仓库所用的同公司连接标识。 */ @Positive long connectionId,
            /* GitLab 数字 ID 或 URL 编码前的完整项目路径。 */ @NotBlank @Size(max = 500) String remoteProjectId) {}

    public record ConfigureWebhookSecretRequest(
            /* 与 GitLab Webhook 配置一致且只在本次请求内存在的 Secret。 */
            @NotBlank @Size(min = 16, max = 2000) String secret) {}

    private long organizationId(HttpServletRequest request) {
        long userId = AuthController.requireContext(request).userId();
        return organizations.requireContext(userId).organizationId();
    }
}
