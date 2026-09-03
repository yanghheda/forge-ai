package ai.forge.server.workspace.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.auth.domain.AuthContext;
import ai.forge.server.workspace.application.WorkspaceCommandService;
import ai.forge.server.workspace.application.WorkspaceQueryService;
import ai.forge.server.workspace.domain.Workspace;
import ai.forge.server.workspace.domain.WorkspaceMember;
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
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/workspaces")
@Tag(name = "Workspace", description = "Workspace 范围、创建与成员关系管理")
public class WorkspaceController {

    /* Workspace 查询服务，所有读取先验证当前 Session 的成员范围。 */
    private final WorkspaceQueryService workspaceQueryService;

    /* Workspace 命令服务，负责 Owner 管理范围内的本地事务写入。 */
    private final WorkspaceCommandService workspaceCommandService;

    public WorkspaceController(
            WorkspaceQueryService workspaceQueryService, WorkspaceCommandService workspaceCommandService) {
        this.workspaceQueryService = workspaceQueryService;
        this.workspaceCommandService = workspaceCommandService;
    }

    @GetMapping
    @Operation(summary = "列出可访问 Workspace", description = "权限：已登录；仅返回当前有效成员关系覆盖的 Workspace。")
    @ApiResponse(responseCode = "200", description = "当前用户可访问的 Workspace 摘要")
    public List<Workspace> list(HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        return workspaceQueryService.list(context.userId());
    }

    @PostMapping
    @Operation(summary = "创建 Workspace", description = "权限：初始化阶段已有 OWNER；创建者在同一事务内成为新 Workspace 的 OWNER。")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Workspace 与创建者成员关系已创建"),
        @ApiResponse(responseCode = "404", description = "当前用户没有 OWNER 管理范围")
    })
    public ResponseEntity<Workspace> create(
            @Valid @RequestBody CreateWorkspaceRequest body, HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        Workspace workspace = workspaceCommandService.create(context.userId(), body.name(), body.slug());
        return ResponseEntity.created(URI.create("/api/v1/workspaces/" + workspace.id())).body(workspace);
    }

    @GetMapping("/{workspaceId}/members")
    @Operation(summary = "列出 Workspace 成员", description = "权限：该 Workspace 的 OWNER；无权或不存在统一返回 404。")
    @ApiResponse(responseCode = "200", description = "成员关系摘要，包括已移除关系的当前状态")
    public List<WorkspaceMember> members(@PathVariable long workspaceId, HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        return workspaceQueryService.members(context.userId(), workspaceId);
    }

    @PostMapping("/{workspaceId}/members")
    @Operation(summary = "添加 Workspace 成员", description = "权限：该 Workspace 的 OWNER；邮箱必须对应当前已存在且有效的本地账号。")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "成员关系已启用或恢复"),
        @ApiResponse(responseCode = "404", description = "Workspace、Owner 范围或已有账号成员关系不存在")
    })
    public ResponseEntity<Void> addMember(
            @PathVariable long workspaceId,
            @Valid @RequestBody AddMemberRequest body,
            HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        workspaceCommandService.addMember(context.userId(), workspaceId, body.email());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{workspaceId}/members/{memberUserId}")
    @Operation(summary = "移除 Workspace 成员", description = "权限：该 Workspace 的 OWNER；移除后后续项目访问会重新校验并失效。")
    @ApiResponse(responseCode = "204", description = "成员关系已标记为 REMOVED")
    public ResponseEntity<Void> removeMember(
            @PathVariable long workspaceId, @PathVariable long memberUserId, HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        workspaceCommandService.removeMember(context.userId(), workspaceId, memberUserId);
        return ResponseEntity.noContent().build();
    }

    public record CreateWorkspaceRequest(
            /* 新 Workspace 的界面展示名称。 */
            @NotBlank @Size(max = 120) String name,
            /* 组织内唯一的小写路由短名。 */
            @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 80) String slug) {}

    public record AddMemberRequest(
            /* 用于定位既有本地账号的电子邮箱，服务端会规范化后查询。 */
            @NotBlank @Email @Size(max = 320) String email) {}
}
