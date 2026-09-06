package ai.forge.server.qa.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.qa.application.QaService;
import ai.forge.server.qa.application.TestCaseView;
import ai.forge.server.qa.application.TestResultView;
import ai.forge.server.qa.application.TestRunView;
import ai.forge.server.qa.domain.TestCasePriority;
import ai.forge.server.qa.domain.TestResultStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Profile("!test-unit")
@RequestMapping("/api/v1/qa")
@Tag(name = "QA", description = "Test Case、Test Run 与确定性质量结论")
public class QaController {

    /* 执行授权、输入规范化与 QA 应用编排。 */
    private final QaService service;

    public QaController(QaService service) {
        this.service = service;
    }

    @PostMapping("/requirements/{requirementId}/cases")
    @Operation(summary = "创建 Test Case")
    public ResponseEntity<TestCaseView> createCase(@PathVariable long requirementId,
            @Valid @RequestBody CreateCaseRequest body, HttpServletRequest request) {
        TestCaseView created = service.createCase(AuthController.requireContext(request).userId(), body.workspaceId(),
                body.projectId(), requirementId, body.title(), body.preconditions(), body.steps(),
                body.expectedResult(), body.priority());
        return ResponseEntity.created(URI.create("/api/v1/qa/cases/" + created.id())).body(created);
    }

    @GetMapping("/requirements/{requirementId}/cases")
    @Operation(summary = "列出 Requirement Test Case")
    public List<TestCaseView> cases(@PathVariable long requirementId, @RequestParam long workspaceId,
            @RequestParam long projectId, HttpServletRequest request) {
        return service.cases(AuthController.requireContext(request).userId(), workspaceId, projectId, requirementId);
    }

    @GetMapping("/requirements/{requirementId}/runs/latest")
    @Operation(summary = "读取 Requirement 最新 Test Run")
    public TestRunView latestRun(@PathVariable long requirementId, @RequestParam long workspaceId,
            @RequestParam long projectId, HttpServletRequest request) {
        return service.latestRun(
                AuthController.requireContext(request).userId(), workspaceId, projectId, requirementId);
    }

    @PostMapping("/requirements/{requirementId}/runs")
    @Operation(summary = "创建 Test Run", description = "将当前 ACTIVE Test Case 固化为 NOT_RUN 结果集合。")
    public ResponseEntity<TestRunView> createRun(@PathVariable long requirementId,
            @Valid @RequestBody CreateRunRequest body, HttpServletRequest request) {
        TestRunView created = service.createRun(AuthController.requireContext(request).userId(), body.workspaceId(),
                body.projectId(), requirementId, body.environment());
        return ResponseEntity.created(URI.create("/api/v1/qa/runs/" + created.id())).body(created);
    }

    @GetMapping("/runs/{runId}")
    @Operation(summary = "读取 Test Run 与结果")
    public TestRunView run(@PathVariable long runId, @RequestParam long workspaceId,
            @RequestParam long projectId, HttpServletRequest request) {
        return service.run(AuthController.requireContext(request).userId(), workspaceId, projectId, runId);
    }

    @PutMapping("/runs/{runId}/results/{resultId}")
    @Operation(summary = "记录 Test Result", description = "已完成 Run 必须先显式 reopen。")
    public TestResultView updateResult(@PathVariable long runId, @PathVariable long resultId,
            @Valid @RequestBody UpdateResultRequest body, HttpServletRequest request) {
        return service.updateResult(AuthController.requireContext(request).userId(), body.workspaceId(),
                body.projectId(), runId, resultId, body.status(), body.actualResult(), body.evidence(),
                body.expectedVersion());
    }

    @PostMapping("/runs/{runId}/complete")
    @Operation(summary = "完成 Test Run 并固化统计")
    public TestRunView complete(@PathVariable long runId, @Valid @RequestBody RunVersionRequest body,
            HttpServletRequest request) {
        return service.completeRun(AuthController.requireContext(request).userId(), body.workspaceId(),
                body.projectId(), runId, body.expectedVersion());
    }

    @PostMapping("/runs/{runId}/reopen")
    @Operation(summary = "重新打开已完成 Test Run", description = "清空固化统计并追加审计记录。")
    public TestRunView reopen(@PathVariable long runId, @Valid @RequestBody ReopenRunRequest body,
            @RequestHeader(value = "X-Request-Id", defaultValue = "unknown") String requestId,
            HttpServletRequest request) {
        return service.reopenRun(AuthController.requireContext(request).userId(), body.workspaceId(),
                body.projectId(), runId, body.expectedVersion(), body.reason(), requestId);
    }

    public record CreateCaseRequest(
            /* Test Case 所属工作区。 */ @Positive long workspaceId,
            /* Test Case 所属项目。 */ @Positive long projectId,
            /* 用例标题。 */ @NotBlank @Size(max = 255) String title,
            /* 执行前置条件。 */ @Size(max = 20000) String preconditions,
            /* 按顺序执行的步骤。 */ @NotEmpty List<@NotBlank @Size(max = 2000) String> steps,
            /* 预期结果。 */ @NotBlank @Size(max = 20000) String expectedResult,
            /* QA 执行优先级。 */ @NotNull TestCasePriority priority) {}

    public record CreateRunRequest(
            /* Test Run 所属工作区。 */ @Positive long workspaceId,
            /* Test Run 所属项目。 */ @Positive long projectId,
            /* 可审计执行环境。 */ @NotBlank @Size(max = 255) String environment) {}

    public record UpdateResultRequest(
            /* Test Result 所属工作区。 */ @Positive long workspaceId,
            /* Test Result 所属项目。 */ @Positive long projectId,
            /* 本次人工执行结论。 */ @NotNull TestResultStatus status,
            /* 实际观察结果。 */ @Size(max = 20000) String actualResult,
            /* 脱敏证据引用。 */ @NotNull List<@NotBlank @Size(max = 2000) String> evidence,
            /* 客户端读取到的结果版本。 */ @PositiveOrZero long expectedVersion) {}

    public record RunVersionRequest(
            /* Test Run 所属工作区。 */ @Positive long workspaceId,
            /* Test Run 所属项目。 */ @Positive long projectId,
            /* 客户端读取到的运行版本。 */ @PositiveOrZero long expectedVersion) {}

    public record ReopenRunRequest(
            /* Test Run 所属工作区。 */ @Positive long workspaceId,
            /* Test Run 所属项目。 */ @Positive long projectId,
            /* 客户端读取到的运行版本。 */ @PositiveOrZero long expectedVersion,
            /* 重新打开的审计原因。 */ @NotBlank @Size(max = 500) String reason) {}
}
