package ai.forge.server.project.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.auth.domain.AuthContext;
import ai.forge.server.project.application.ProjectCommandService;
import ai.forge.server.project.application.ProjectQueryService;
import ai.forge.server.project.application.ProjectPolicyService;
import ai.forge.server.project.domain.Project;
import ai.forge.server.project.domain.ProjectMember;
import ai.forge.server.project.domain.ProjectPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/projects")
@Tag(name = "Project", description = "项目创建、查询、归档与项目成员访问范围")
public class ProjectController {

    /* 项目查询服务，读取时验证 Workspace 与 Project Member 双重范围。 */
    private final ProjectQueryService projectQueryService;

    /* 项目命令服务，写入时验证 Owner 范围和乐观锁。 */
    private final ProjectCommandService projectCommandService;

    /* 管理默认拒绝并使用乐观锁更新的项目工作流策略。 */
    private final ProjectPolicyService projectPolicyService;

    public ProjectController(
            ProjectQueryService projectQueryService,
            ProjectCommandService projectCommandService,
            ProjectPolicyService projectPolicyService) {
        this.projectQueryService = projectQueryService;
        this.projectCommandService = projectCommandService;
        this.projectPolicyService = projectPolicyService;
    }

    @GetMapping
    @Operation(summary = "列出 Workspace 项目", description = "权限：有效 Workspace Member；仅返回当前用户具备项目范围的项目。")
    @ApiResponse(responseCode = "200", description = "当前用户可访问的项目摘要")
    public List<Project> list(@RequestParam long workspaceId, HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        return projectQueryService.list(context.userId(), workspaceId);
    }

    @PostMapping
    @Operation(summary = "创建 Project", description = "权限：Workspace OWNER；项目与创建者 Project Member 在同一事务内创建。")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Project 已创建"),
        @ApiResponse(responseCode = "409", description = "同一 Workspace 的项目 key 已存在"),
        @ApiResponse(responseCode = "404", description = "Workspace 不存在或当前用户没有 OWNER 范围")
    })
    public ResponseEntity<Project> create(@Valid @RequestBody CreateProjectRequest body, HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        Project project = projectCommandService.create(
                context.userId(), body.workspaceId(), body.key(), body.name(), body.description());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + project.id())).body(project);
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "读取 Project", description = "权限：有效 Workspace Member 且为 Project Member，或为 Workspace OWNER；无权统一返回 404。")
    @ApiResponse(responseCode = "200", description = "带版本与归档状态的项目详情")
    public Project get(
            @PathVariable long projectId, @RequestParam long workspaceId, HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        return projectQueryService.get(context.userId(), workspaceId, projectId);
    }

    @PatchMapping("/{projectId}")
    @Operation(summary = "归档 Project", description = "权限：Workspace OWNER；请求必须带 expectedVersion，归档不是删除。")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "项目已归档并递增版本"),
        @ApiResponse(responseCode = "409", description = "项目版本已变化或已归档"),
        @ApiResponse(responseCode = "404", description = "项目不在请求 Workspace 范围内或没有 OWNER 范围")
    })
    public ResponseEntity<Void> archive(
            @PathVariable long projectId,
            @RequestParam long workspaceId,
            @Valid @RequestBody ArchiveProjectRequest body,
            HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        projectCommandService.archive(context.userId(), workspaceId, projectId, body.expectedVersion());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/members")
    @Operation(summary = "列出 Project 成员", description = "权限：Workspace OWNER；成员关系本身只定义范围，不定义会话 10 的角色。")
    @ApiResponse(responseCode = "200", description = "项目成员关系摘要")
    public List<ProjectMember> members(
            @PathVariable long projectId, @RequestParam long workspaceId, HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        return projectQueryService.members(context.userId(), workspaceId, projectId);
    }

    @PostMapping("/{projectId}/members")
    @Operation(summary = "添加 Project 成员", description = "权限：Workspace OWNER；目标邮箱必须是当前有效 Workspace Member。")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "项目成员关系已启用或恢复"),
        @ApiResponse(responseCode = "404", description = "项目、Owner 范围或有效 Workspace Member 不存在")
    })
    public ResponseEntity<Void> addMember(
            @PathVariable long projectId,
            @RequestParam long workspaceId,
            @Valid @RequestBody AddProjectMemberRequest body,
            HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        projectCommandService.addMember(context.userId(), workspaceId, projectId, body.email());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{projectId}/members/{memberUserId}")
    @Operation(summary = "移除 Project 成员", description = "权限：Workspace OWNER；后续项目读取会重新校验成员关系并失效。")
    @ApiResponse(responseCode = "204", description = "项目成员关系已标记为 REMOVED")
    public ResponseEntity<Void> removeMember(
            @PathVariable long projectId,
            @PathVariable long memberUserId,
            @RequestParam long workspaceId,
            HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        projectCommandService.removeMember(context.userId(), workspaceId, projectId, memberUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/policy")
    public ProjectPolicy policy(
            @PathVariable long projectId,
            @RequestParam long workspaceId,
            HttpServletRequest request) {
        return projectPolicyService.get(
                AuthController.requireContext(request).userId(), workspaceId, projectId);
    }

    @PatchMapping("/{projectId}/policy")
    public ProjectPolicy updatePolicy(
            @PathVariable long projectId,
            @RequestParam long workspaceId,
            @Valid @RequestBody UpdateProjectPolicyRequest body,
            HttpServletRequest request) {
        return projectPolicyService.update(
                AuthController.requireContext(request).userId(),
                workspaceId,
                projectId,
                body.allowSkipUx(),
                body.expectedVersion());
    }

    public record CreateProjectRequest(
            /* 项目所属 Workspace；服务端会与当前成员范围重新比对。 */
            @NotNull @PositiveOrZero Long workspaceId,
            /* Workspace 内唯一的项目短键；服务端会转为大写。 */
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{0,31}") String key,
            /* 项目的界面展示名称。 */
            @NotBlank @Size(max = 160) String name,
            /* 项目的可选说明；空值由服务端规范为无说明。 */
            @Size(max = 2000) String description) {}

    public record ArchiveProjectRequest(
            /* 客户端基于读取版本提交的乐观锁预期值。 */
            @PositiveOrZero long expectedVersion) {}

    public record AddProjectMemberRequest(
            /* 用于定位既有有效 Workspace Member 的电子邮箱。 */
            @NotBlank @Email @Size(max = 320) String email) {}

    public record UpdateProjectPolicyRequest(
            /* 是否启用受分类与原因约束的 UX 跳过路径。 */ boolean allowSkipUx,
            /* 客户端读取到的策略版本。 */ @PositiveOrZero long expectedVersion) {}
}
