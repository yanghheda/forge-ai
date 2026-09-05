package ai.forge.server.gitlab.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.gitlab.application.PipelineService;
import ai.forge.server.gitlab.domain.PipelineRun;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/development/pipelines")
@Tag(name = "Pipeline", description = "GitLab Pipeline 触发、快照与有界日志尾部")
public class PipelineController {

    /* 编排 Pipeline 授权、外部调用和本地快照。 */
    private final PipelineService service;

    public PipelineController(PipelineService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "触发 Pipeline", description = "权限：repo.write；远端调用不在数据库事务中。")
    public PipelineRun trigger(@Valid @RequestBody TriggerPipelineRequest body, HttpServletRequest request) {
        return service.trigger(AuthController.requireContext(request).userId(), body.workspaceId(),
                body.projectId(), body.ref());
    }

    @GetMapping
    @Operation(summary = "查看 Pipeline 快照", description = "权限：repo.read；最多返回最近 50 条。")
    public List<PipelineRun> list(@RequestParam long workspaceId, @RequestParam long projectId,
            HttpServletRequest request) {
        return service.list(AuthController.requireContext(request).userId(), workspaceId, projectId);
    }

    @GetMapping("/{pipelineId}/jobs/{jobId}/log-tail")
    @Operation(summary = "查看 Job 日志尾部", description = "权限：repo.read；响应按 UTF-8 字节上限截断。")
    public PipelineService.PipelineLogTail logTail(
            @PathVariable long pipelineId,
            @PathVariable long jobId,
            @RequestParam long workspaceId,
            @RequestParam long projectId,
            HttpServletRequest request) {
        return service.logTail(AuthController.requireContext(request).userId(), workspaceId, projectId,
                pipelineId, jobId);
    }

    public record TriggerPipelineRequest(
            /* Pipeline 所属工作区。 */ @Positive long workspaceId,
            /* Pipeline 所属 ForgeAI 项目。 */ @Positive long projectId,
            /* 触发 Pipeline 的分支或标签。 */ @NotBlank @Size(max = 255) String ref) {}
}
