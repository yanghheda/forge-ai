package ai.forge.server.organization.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.organization.application.OrganizationMemberService;
import ai.forge.server.organization.domain.OrganizationMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/members")
@Tag(name = "公司成员", description = "公司级成员审核、启停和角色治理")
public class OrganizationMemberController {
    /* 执行成员治理权限、状态和角色规则的服务。 */
    private final OrganizationMemberService memberService;

    public OrganizationMemberController(OrganizationMemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    @Operation(summary = "列出公司成员", description = "返回有效、待审核和已停用成员。")
    public List<OrganizationMember> list(HttpServletRequest request) {
        return memberService.list(AuthController.requireContext(request).userId());
    }

    @PatchMapping("/{userId}")
    @Operation(summary = "审核或编辑成员", description = "权限：member.manage；使用 expectedVersion 防止覆盖并发修改。")
    public OrganizationMember update(
            @PathVariable long userId,
            @Valid @RequestBody UpdateMemberRequest body,
            HttpServletRequest request) {
        return memberService.update(
                AuthController.requireContext(request).userId(),
                userId,
                body.status(),
                body.role(),
                body.expectedVersion());
    }

    public record UpdateMemberRequest(
            /* 审核后的成员状态。 */ @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
            /* 成员承担的业务角色。 */
            @NotBlank @Pattern(regexp = "PRODUCT|UX|DEVELOPER|QA|RELEASE_APPROVER") String role,
            /* 客户端读取成员时获得的版本。 */ @Min(0) long expectedVersion) {}

}
