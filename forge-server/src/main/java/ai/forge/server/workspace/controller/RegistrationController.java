package ai.forge.server.workspace.controller;

import ai.forge.server.workspace.application.WorkspaceCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/auth/register")
@Tag(name = "成员注册", description = "初始化后由团队成员自行注册业务角色账号")
public class RegistrationController {

    /* 原子创建用户、默认组织成员关系、业务角色和内部项目范围。 */
    private final WorkspaceCommandService commandService;

    public RegistrationController(WorkspaceCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping
    @Operation(summary = "自助注册", description = "无需登录；只允许 PRODUCT、UX、DEVELOPER、QA，不能自助取得管理角色。")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "账号与业务角色已创建，可立即登录"),
        @ApiResponse(responseCode = "400", description = "字段、密码或角色不符合约束"),
        @ApiResponse(responseCode = "409", description = "邮箱已被注册")
    })
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest body) {
        long userId = commandService.selfRegister(body.email(), body.displayName(), body.password(), body.role());
        return ResponseEntity.created(URI.create("/api/v1/users/" + userId)).body(new RegisterResponse(userId));
    }

    public record RegisterRequest(
            /* 新成员用于登录且全实例唯一的邮箱。 */ @NotBlank @Email @Size(max = 320) String email,
            /* 新成员在需求参与人等界面展示的名称。 */ @NotBlank @Size(max = 120) String displayName,
            /* 只用于生成 BCrypt 摘要且永不回显的密码。 */ @NotBlank String password,
            /* 自助选择的业务角色代码，管理角色不匹配此白名单。 */
            @NotBlank @Pattern(regexp = "PRODUCT|UX|DEVELOPER|QA") String role) {}

    public record RegisterResponse(
            /* 注册成功后创建的稳定用户标识。 */ long userId) {}
}
