package ai.forge.server.auth.controller;

import ai.forge.server.auth.application.InstanceBootstrapService;
import ai.forge.server.auth.domain.BootstrapCommand;
import ai.forge.server.auth.domain.BootstrapResult;
import ai.forge.server.auth.domain.CompanyLogoPolicy;
import ai.forge.server.platform.web.RequestIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Base64;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/setup")
@Tag(name = "实例初始化", description = "仅供尚未初始化的私有实例创建首个 Owner")
public class SetupController {

    /* 初始化应用服务，承载唯一性判断、密码处理和原子写入。 */
    private final InstanceBootstrapService bootstrapService;

    public SetupController(InstanceBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @GetMapping("/status")
    @Operation(summary = "查询初始化状态", description = "权限：无需登录；只读取实例是否已完成首次初始化。")
    @ApiResponse(responseCode = "200", description = "返回当前实例初始化状态")
    public SetupStatusResponse status() {
        return new SetupStatusResponse(bootstrapService.isInitialized());
    }

    @PostMapping("/initialize")
    @Operation(
            summary = "初始化 ForgeAI 实例",
            description = "权限：无需登录且仅未初始化实例可调用；不使用幂等键，首次成功后永久返回冲突。")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "首个 Owner、Organization 与 Workspace 已原子创建"),
        @ApiResponse(responseCode = "400", description = "字段格式或密码安全要求不满足"),
        @ApiResponse(responseCode = "409", description = "实例已经初始化")
    })
    public ResponseEntity<InitializeResponse> initialize(
            @Valid @RequestBody InitializeRequest body, HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        BootstrapResult result = bootstrapService.initialize(new BootstrapCommand(
                body.adminEmail(),
                body.adminDisplayName(),
                body.password(),
                body.organizationName(),
                body.organizationSlug(),
                body.workspaceName() == null ? body.organizationName() : body.workspaceName(),
                body.workspaceSlug() == null ? body.organizationSlug() : body.workspaceSlug(),
                requestId), decodeLogo(body));
        InitializeResponse response = new InitializeResponse(
                result.userId(),
                result.organizationId(),
                result.organizationSlug());
        return ResponseEntity.created(URI.create("/api/v1/organization")).body(response);
    }

    public record InitializeRequest(
            /* 首个 Owner 的有效电子邮箱地址。 */
            @NotBlank @Email @Size(max = 320) String adminEmail,
            /* 首个 Owner 在界面显示的名称。 */
            @NotBlank @Size(max = 120) String adminDisplayName,
            /* 仅用于生成 BCrypt 摘要的密码；响应与日志均不得回显。 */
            @NotBlank String password,
            /* 默认组织的界面展示名称。 */
            @NotBlank @Size(max = 120) String organizationName,
            /* 默认组织的小写字母、数字和短横线路由短名。 */
            @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 80) String organizationSlug,
            /* 旧客户端兼容字段；新产品入口不再展示工作区。 */
            @Size(max = 120) String workspaceName,
            /* 旧客户端兼容字段；新产品入口不再展示工作区短名。 */
            @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 80) String workspaceSlug,
            /* 公司 Logo 的原始文件名，仅允许使用 .webp 后缀。 */
            @NotBlank @Pattern(regexp = ".+\\.[wW][eE][bB][pP]") String logoFileName,
            /* 公司 Logo 的媒体类型，必须精确为 image/webp。 */
            @NotBlank @Pattern(regexp = "image/webp") String logoMediaType,
            /* 公司 Logo 的 Base64 内容；解码后上限为 2 MiB。 */
            @NotBlank @Size(max = 2_796_204) String logoBase64) {}

    public record InitializeResponse(
            /* 初始化创建的首个 Owner 用户标识。 */
            long userId,
            /* 初始化创建的默认组织标识。 */
            long organizationId,
            /* 默认组织的稳定路由短名。 */
            String organizationSlug) {}

    public record SetupStatusResponse(
            /* 为 true 时公开初始化入口已永久关闭，Web 应展示登录表单。 */
            boolean initialized) {}

    private byte[] decodeLogo(InitializeRequest body) {
        try {
            byte[] content = Base64.getDecoder().decode(body.logoBase64());
            CompanyLogoPolicy.validate(content);
            return content;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid WebP logo", exception);
        }
    }

}
