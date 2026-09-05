package ai.forge.server.gitlab.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.gitlab.application.DevelopmentResult;
import ai.forge.server.gitlab.application.DevelopmentService;
import ai.forge.server.workitem.domain.WorkItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Profile("!test-unit")
@RequestMapping("/api/v1/development")
@Tag(name = "Development", description = "Dev Task 与 GitLab Branch/MR 启动编排")
public class DevelopmentController {

    /* 执行状态、权限、幂等与外部资源 reconcile 的应用服务。 */
    private final DevelopmentService service;

    public DevelopmentController(DevelopmentService service) {
        this.service = service;
    }

    @PostMapping("/requirements/{requirementId}/tasks")
    @Operation(summary = "创建 Dev Task", description = "仅允许在 READY_FOR_DEV Requirement 下创建。")
    public ResponseEntity<WorkItem> createTask(
            @PathVariable long requirementId,
            @Valid @RequestBody CreateDevTaskRequest body,
            HttpServletRequest request) {
        WorkItem item = service.createDevTask(AuthController.requireContext(request).userId(), body.workspaceId(),
                body.projectId(), requirementId, body.title(), body.description(), body.assigneeUserId());
        return ResponseEntity.created(URI.create("/api/v1/work-items/" + item.id())).body(item);
    }

    @PostMapping("/tasks/{taskId}/start")
    @Operation(summary = "启动开发", description = "创建或复用 Branch/MR，成功后关联并推进本地工作项状态。")
    public DevelopmentResult start(
            @PathVariable long taskId,
            @Valid @RequestBody StartDevelopmentRequest body,
            HttpServletRequest request) {
        return service.start(AuthController.requireContext(request).userId(), body.workspaceId(), body.projectId(),
                taskId, body.targetBranch(), body.idempotencyKey());
    }

    public record CreateDevTaskRequest(
            /* Dev Task 所属工作区。 */ @Positive long workspaceId,
            /* Dev Task 所属项目。 */ @Positive long projectId,
            /* 研发任务标题。 */ @NotBlank @Size(max = 255) String title,
            /* 研发任务实现说明；未填写时规范为空字符串。 */ @Size(max = 20000) String description,
            /* 可选负责人，必须是项目有效成员。 */ @Positive Long assigneeUserId) {}

    public record StartDevelopmentRequest(
            /* Dev Task 所属工作区。 */ @Positive long workspaceId,
            /* Dev Task 所属项目。 */ @Positive long projectId,
            /* 可选目标分支；为空时使用仓库默认分支。 */ @Size(max = 255) String targetBranch,
            /* 项目范围内稳定的调用幂等键。 */ @NotBlank @Size(max = 128) String idempotencyKey) {}
}
