package ai.forge.server.auth.controller;

import ai.forge.server.auth.application.CurrentUserQuery;
import ai.forge.server.auth.domain.AuthContext;
import ai.forge.server.auth.domain.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/me")
@Tag(name = "当前用户", description = "读取当前 Session 对应用户及其实时有效 Workspace 范围")
public class CurrentUserController {

    /* 从 MySQL 查询当前有效用户、成员关系和 Workspace 级角色。 */
    private final CurrentUserQuery currentUserQuery;

    public CurrentUserController(CurrentUserQuery currentUserQuery) {
        this.currentUserQuery = currentUserQuery;
    }

    @GetMapping
    @Operation(summary = "查询当前用户", description = "权限：已登录；成员关系和角色始终从 MySQL 当前事实解析。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "当前用户与有效 Workspace 范围"),
        @ApiResponse(responseCode = "401", description = "当前请求没有有效 Session")
    })
    public CurrentUser me(HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        return currentUserQuery.get(context.userId());
    }
}
