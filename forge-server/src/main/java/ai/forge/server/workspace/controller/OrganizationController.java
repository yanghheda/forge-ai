package ai.forge.server.workspace.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.workspace.application.WorkspaceAccessService;
import ai.forge.server.workspace.domain.OrganizationScope;
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
@Tag(name = "当前组织", description = "单组织产品入口，不暴露内部兼容 scope")
public class OrganizationController {

    /* 从 Session 用户和数据库默认事实解析唯一可见组织。 */
    private final WorkspaceAccessService accessService;

    public OrganizationController(WorkspaceAccessService accessService) {
        this.accessService = accessService;
    }

    @GetMapping
    @Operation(summary = "读取当前组织", description = "权限：已登录且是当前默认组织的有效成员。")
    public OrganizationResponse get(HttpServletRequest request) {
        OrganizationScope scope = accessService.requireDefaultScope(AuthController.requireContext(request).userId());
        return new OrganizationResponse(scope.organizationId(), scope.organizationName(), scope.owner());
    }

    public record OrganizationResponse(
            /* 当前实例唯一可见组织标识。 */ long id,
            /* 当前组织展示名称。 */ String name,
            /* 当前用户是否为组织 Owner。 */ boolean owner) {}
}
