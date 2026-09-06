package ai.forge.server.qa.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.qa.application.BugService;
import ai.forge.server.qa.application.BugView;
import ai.forge.server.qa.domain.BugAction;
import ai.forge.server.qa.domain.BugSeverity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Profile("!test-unit")
@RequestMapping("/api/v1/bugs")
public class BugController {

    /* 编排 Bug 创建、查询和状态迁移。 */
    private final BugService service;

    public BugController(BugService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BugView> create(@Valid @RequestBody CreateBugRequest body,
            HttpServletRequest request) {
        BugView bug = service.create(AuthController.requireContext(request).userId(), body.workspaceId(),
                body.projectId(), body.requirementId(), body.testRunId(), body.testResultId(), body.devTaskId(),
                body.title(), body.severity(), body.reproductionSteps(), body.expectedResult(), body.actualResult());
        return ResponseEntity.created(URI.create("/api/v1/bugs/" + bug.id())).body(bug);
    }

    @GetMapping("/{bugId}")
    public BugView get(@PathVariable long bugId, @RequestParam long workspaceId,
            @RequestParam long projectId, HttpServletRequest request) {
        return service.get(AuthController.requireContext(request).userId(), workspaceId, projectId, bugId);
    }

    @GetMapping
    public List<BugView> list(@RequestParam long workspaceId, @RequestParam long projectId,
            @RequestParam long requirementId, HttpServletRequest request) {
        return service.list(AuthController.requireContext(request).userId(), workspaceId, projectId, requirementId);
    }

    @PostMapping("/{bugId}/transitions")
    public BugView transition(@PathVariable long bugId, @Valid @RequestBody BugTransitionRequest body,
            HttpServletRequest request) {
        return service.transition(AuthController.requireContext(request).userId(), body.workspaceId(),
                body.projectId(), bugId, body.action(), body.expectedVersion(), body.reason(), body.fixEvidence(),
                body.idempotencyKey());
    }

    public record CreateBugRequest(
            /* Bug 所属工作区。 */ @Positive long workspaceId,
            /* Bug 所属项目。 */ @Positive long projectId,
            /* Bug 关联 Requirement。 */ @Positive long requirementId,
            /* 来源 Test Run；人工草稿为空。 */ Long testRunId,
            /* 来源失败 Test Result；人工草稿为空。 */ Long testResultId,
            /* 可选关联修复 Dev Task。 */ Long devTaskId,
            /* Bug 标题。 */ @NotBlank @Size(max = 255) String title,
            /* 缺陷严重级别。 */ @NotNull BugSeverity severity,
            /* 可执行复现步骤。 */ @NotEmpty List<@NotBlank @Size(max = 2000) String> reproductionSteps,
            /* 测试期望结果。 */ @NotBlank @Size(max = 20000) String expectedResult,
            /* 实际失败结果。 */ @NotBlank @Size(max = 20000) String actualResult) {}

    public record BugTransitionRequest(
            /* Bug 所属工作区。 */ @Positive long workspaceId,
            /* Bug 所属项目。 */ @Positive long projectId,
            /* 请求的固定 Bug 动作。 */ @NotNull BugAction action,
            /* 客户端读取的 Work Item 版本。 */ @PositiveOrZero long expectedVersion,
            /* 修复说明或 reopen 原因。 */ @Size(max = 20000) String reason,
            /* MR、Commit 等修复证据。 */ List<@NotBlank @Size(max = 2000) String> fixEvidence,
            /* 同一 Bug 内唯一的迁移重试标识。 */ @NotBlank @Size(max = 128) String idempotencyKey) {}

}
