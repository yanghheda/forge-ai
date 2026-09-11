package ai.forge.server.gitlab.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.gitlab.application.DevelopmentResult;
import ai.forge.server.gitlab.application.DevelopmentService;
import ai.forge.server.organization.application.OrganizationAccessService;
import ai.forge.server.workitem.application.DevelopmentQaQuery;
import ai.forge.server.workitem.application.DevelopmentQaSummary;
import ai.forge.server.workitem.domain.WorkItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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

    /* 返回与 QA Guard 使用同一事实投影的开发页面汇总。 */
    private final DevelopmentQaQuery developmentQaQuery;
    /* 从登录身份解析唯一公司作用域。 */
    private final OrganizationAccessService organizations;

    public DevelopmentController(DevelopmentService service, DevelopmentQaQuery developmentQaQuery,
            OrganizationAccessService organizations) {
        this.service = service;
        this.developmentQaQuery = developmentQaQuery;
        this.organizations = organizations;
    }

    @PostMapping("/requirements/{requirementId}/tasks")
    @Operation(summary = "创建 Dev Task", description = "仅允许在 READY_FOR_DEV Requirement 下创建。")
    public ResponseEntity<WorkItem> createTask(
            @PathVariable long requirementId,
            @Valid @RequestBody CreateDevTaskRequest body,
            HttpServletRequest request) {
        WorkItem item = service.createDevTask(AuthController.requireContext(request).userId(), organizationId(request), requirementId, body.title(), body.description(), body.assigneeUserId());
        return ResponseEntity.created(URI.create("/api/v1/work-items/" + item.id())).body(item);
    }

    @PostMapping("/tasks/{taskId}/start")
    @Operation(summary = "启动开发", description = "创建或复用 Branch/MR，成功后关联并推进本地工作项状态。")
    public DevelopmentResult start(
            @PathVariable long taskId,
            @Valid @RequestBody StartDevelopmentRequest body,
            HttpServletRequest request) {
        return service.start(AuthController.requireContext(request).userId(), organizationId(request),
                taskId, body.targetBranch(), body.idempotencyKey());
    }

    @PostMapping("/tasks/{taskId}/complete")
    @Operation(summary = "完成 Dev Task", description = "仅允许完成 IN_DEVELOPMENT Requirement 下的 IN_PROGRESS 任务。")
    public WorkItem completeTask(
            @PathVariable long taskId,
            @Valid @RequestBody CompleteDevTaskRequest body,
            HttpServletRequest request) {
        return service.completeTask(
                AuthController.requireContext(request).userId(),
                organizationId(request),
                taskId,
                body.expectedVersion());
    }

    @org.springframework.web.bind.annotation.GetMapping("/requirements/{requirementId}")
    @Operation(summary = "读取 Development 汇总", description = "返回 Dev Task、Branch、MR、Pipeline 与 CI 策略快照。")
    public DevelopmentQaSummary summary(
            @PathVariable long requirementId, HttpServletRequest request) {
        return developmentQaQuery.get(
                AuthController.requireContext(request).userId(),
                organizationId(request),
                requirementId);
    }

    public record CreateDevTaskRequest(
            /* 研发任务标题。 */ @NotBlank @Size(max = 255) String title,
            /* 研发任务实现说明；未填写时规范为空字符串。 */ @Size(max = 20000) String description,
            /* 可选负责人，必须是公司有效成员。 */ @Positive Long assigneeUserId) {}

    public record StartDevelopmentRequest(
            /* 可选目标分支；为空时使用仓库默认分支。 */ @Size(max = 255) String targetBranch,
            /* 公司范围内稳定的调用幂等键。 */ @NotBlank @Size(max = 128) String idempotencyKey) {}

    public record CompleteDevTaskRequest(
            /* 客户端读取到的 Dev Task 聚合版本。 */ @PositiveOrZero long expectedVersion) {}

    private long organizationId(HttpServletRequest request) {
        long userId = AuthController.requireContext(request).userId();
        return organizations.requireContext(userId).organizationId();
    }
}
