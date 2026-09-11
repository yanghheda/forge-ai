package ai.forge.server.organization.controller;

import ai.forge.server.organization.application.OrganizationMemberService;
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
@Tag(name = "成员注册", description = "申请加入实例唯一公司并等待管理员审核")
public class RegistrationController {
    /* 创建待审核公司成员账号的服务。 */
    private final OrganizationMemberService memberService;

    public RegistrationController(OrganizationMemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping
    @Operation(summary = "申请加入公司", description = "无需登录；注册后必须由管理员审核启用。")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "账号申请已创建并处于待审核状态"),
        @ApiResponse(responseCode = "400", description = "字段、密码或角色不符合约束"),
        @ApiResponse(responseCode = "409", description = "邮箱已被注册")
    })
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest body) {
        long userId = memberService.selfRegister(body.email(), body.displayName(), body.password(), body.role());
        return ResponseEntity.created(URI.create("/api/v1/members/" + userId)).body(new RegisterResponse(userId, "PENDING"));
    }

    public record RegisterRequest(
            /* 新成员用于登录且全实例唯一的邮箱。 */ @NotBlank @Email @Size(max = 320) String email,
            /* 新成员在需求参与人等界面展示的名称。 */ @NotBlank @Size(max = 120) String displayName,
            /* 只用于生成 BCrypt 摘要且永不回显的密码。 */ @NotBlank String password,
            /* 申请承担的业务角色代码。 */
            @NotBlank @Pattern(regexp = "PRODUCT|UX|DEVELOPER|QA|RELEASE_APPROVER") String role) {}

    public record RegisterResponse(
            /* 注册申请创建的稳定用户标识。 */ long userId,
            /* 新成员固定为待审核状态。 */ String status) {}
}
