package ai.forge.server.organization.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.organization.application.OrganizationAccessService;
import ai.forge.server.organization.domain.OrganizationContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/organization")
@Tag(name = "当前公司", description = "读取私有实例唯一公司的当前成员上下文")
public class OrganizationController {
    /* 从 Session 用户解析唯一公司的服务。 */
    private final OrganizationAccessService accessService;

    public OrganizationController(OrganizationAccessService accessService) {
        this.accessService = accessService;
    }

    @GetMapping
    @Operation(summary = "读取当前公司", description = "权限：已登录且为当前公司的有效成员。")
    public OrganizationResponse get(HttpServletRequest request) {
        OrganizationContext context = accessService.requireContext(AuthController.requireContext(request).userId());
        return new OrganizationResponse(context.organizationId(), context.organizationName(), context.owner());
    }

    public record OrganizationResponse(
            /* 当前实例唯一公司标识。 */ long id,
            /* 当前公司展示名称。 */ String name,
            /* 当前用户是否为公司 Owner。 */ boolean owner) {}
}
