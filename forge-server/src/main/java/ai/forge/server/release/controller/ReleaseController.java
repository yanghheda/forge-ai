package ai.forge.server.release.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.organization.application.OrganizationAccessService;
import ai.forge.server.release.application.PrecheckSnapshot;
import ai.forge.server.release.application.ReleaseService;
import ai.forge.server.release.application.ReleaseView;
import ai.forge.server.release.application.DeploymentService;
import ai.forge.server.release.application.DeploymentView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Profile("!test-unit")
@RequestMapping("/api/v1/releases")
@Tag(name = "Release", description = "Release Candidate、确定性 Precheck 与 Release Note")
public class ReleaseController {

    /* 发布授权、聚合与确定性检查的应用服务。 */
    private final ReleaseService service;
    /* HIGH 审批与模拟部署应用服务。 */
    private final DeploymentService deployments;
    /* 从登录身份解析唯一公司作用域。 */
    private final OrganizationAccessService organizations;

    public ReleaseController(ReleaseService service, DeploymentService deployments,
            OrganizationAccessService organizations) {
        this.service = service;
        this.deployments = deployments;
        this.organizations = organizations;
    }

    @PostMapping
    @Operation(summary = "创建 Release Candidate")
    public ResponseEntity<ReleaseView> create(@Valid @RequestBody CreateReleaseRequest body,
            HttpServletRequest request) {
        ReleaseView created = service.create(AuthController.requireContext(request).userId(), organizationId(request), body.versionName(), body.environment(), body.itemIds(), body.approvalTtlMinutes());
        return ResponseEntity.created(URI.create("/api/v1/releases/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "列出公司 Release Candidate")
    public List<ReleaseView> list(HttpServletRequest request) {
        return service.list(AuthController.requireContext(request).userId(), organizationId(request));
    }

    @GetMapping("/{releaseId}")
    @Operation(summary = "读取 Release Candidate 与最新 Precheck")
    public ReleaseView get(@PathVariable long releaseId, HttpServletRequest request) {
        return service.get(AuthController.requireContext(request).userId(), organizationId(request), releaseId);
    }

    @PutMapping("/{releaseId}/note")
    @Operation(summary = "编辑 Release Note", description = "编辑会递增 Release version，使旧 Precheck 失效。")
    public ReleaseView updateNote(@PathVariable long releaseId, @Valid @RequestBody UpdateNoteRequest body,
            HttpServletRequest request) {
        return service.updateNote(AuthController.requireContext(request).userId(), organizationId(request), releaseId, body.note(), body.expectedVersion());
    }

    @PostMapping("/{releaseId}/prechecks")
    @Operation(summary = "运行确定性 Precheck", description = "每次执行追加不可变快照，不复用 Agent 结论。")
    public PrecheckSnapshot precheck(@PathVariable long releaseId, HttpServletRequest request) {
        return service.precheck(AuthController.requireContext(request).userId(), organizationId(request), releaseId);
    }

    @PostMapping("/{releaseId}/deployments")
    @Operation(summary = "申请模拟部署", description = "只创建 SIMULATED 部署与 HIGH 审批，不执行生产发布。")
    public DeploymentView requestDeployment(@PathVariable long releaseId,
            @Valid @RequestBody DeploymentRequest body, HttpServletRequest request) {
        return deployments.request(AuthController.requireContext(request).userId(), organizationId(request), releaseId, body.simulateFailure(), body.idempotencyKey(), requestId(request));
    }

    @GetMapping("/{releaseId}/deployments")
    @Operation(summary = "列出模拟部署记录")
    public List<DeploymentView> listDeployments(@PathVariable long releaseId, HttpServletRequest request) {
        return deployments.list(AuthController.requireContext(request).userId(), organizationId(request), releaseId);
    }

    @PostMapping("/deployments/{deploymentId}:decide")
    @Operation(summary = "决定 HIGH 模拟部署审批")
    public DeploymentView decideDeployment(@PathVariable long deploymentId,
            @Valid @RequestBody DeploymentDecisionRequest body, HttpServletRequest request) {
        return deployments.decide(AuthController.requireContext(request).userId(), organizationId(request), deploymentId, "APPROVE".equals(body.decision()), body.expectedVersion(),
                requestId(request));
    }

    public record CreateReleaseRequest(
            /* 公司与环境内唯一版本名。 */ @NotBlank @Size(max = 128) String versionName,
            /* 候选发布目标环境。 */ @NotBlank @Size(max = 64) String environment,
            /* 纳入发布的 Requirement 标识集合。 */ @NotEmpty List<@Positive Long> itemIds,
            /* 固化到策略快照的审批有效期分钟数。 */ @Positive long approvalTtlMinutes) {}

    public record UpdateNoteRequest(
            /* 可编辑但必须非空的 Release Note。 */ @NotBlank @Size(max = 100000) String note,
            /* 客户端读取到的 Release version。 */ @PositiveOrZero long expectedVersion) {}

    public record DeploymentRequest(
            /* 确定性失败演示开关；仍不连接真实环境。 */ boolean simulateFailure,
            /* 防止重复点击产生多个部署请求。 */ @NotBlank @Size(max = 128) String idempotencyKey) {}

    public record DeploymentDecisionRequest(
            /* 审批决定，只允许 APPROVE 或 REJECT。 */ @jakarta.validation.constraints.Pattern(
                    regexp = "APPROVE|REJECT") String decision,
            /* 部署审批乐观锁版本。 */ @PositiveOrZero long expectedVersion) {}

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? java.util.UUID.randomUUID().toString() : value.toString();
    }

    private long organizationId(HttpServletRequest request) {
        long userId = AuthController.requireContext(request).userId();
        return organizations.requireContext(userId).organizationId();
    }
}
