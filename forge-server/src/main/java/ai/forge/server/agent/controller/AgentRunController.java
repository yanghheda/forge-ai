package ai.forge.server.agent.controller;

import ai.forge.server.agent.application.AgentRunService;
import ai.forge.server.agent.application.AgentRunSnapshot;
import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.agent.domain.MediumToolConfirmation;
import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.auth.domain.AuthContext;
import ai.forge.server.organization.application.OrganizationAccessService;
import ai.forge.server.platform.web.RequestIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/agent-runs")
@Tag(name = "Agent Run", description = "创建并读取 Backend 权威 Agent Run 快照")
public class AgentRunController {

    /* 执行 Run scope 授权、创建和快照查询。 */
    private final AgentRunService runService;
    /* 从登录成员解析唯一公司范围。 */
    private final OrganizationAccessService organizationAccess;

    public AgentRunController(AgentRunService runService, OrganizationAccessService organizationAccess) {
        this.runService = runService;
        this.organizationAccess = organizationAccess;
    }

    @PostMapping
    @Operation(summary = "创建 Agent Run", description = "原始 message 不进入持久 Trace；本轮由 Fake Runner 异步执行。")
    public ResponseEntity<AgentRunSnapshot> create(
            @Valid @RequestBody CreateAgentRunRequest body, HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        AgentRunSnapshot snapshot = runService.create(
                context.userId(),
                organizationAccess.requireContext(context.userId()).organizationId(),
                body.workItemId(),
                body.skill(),
                body.mediumToolConfirmation(),
                body.message(),
                body.clientRequestId(),
                requestId);
        return ResponseEntity.created(URI.create("/api/v1/agent-runs/" + snapshot.id())).body(snapshot);
    }

    @GetMapping("/{runId}")
    @Operation(summary = "读取 Agent Run 快照", description = "返回权威状态、lastSequence 与脱敏 Step Trace。")
    public AgentRunSnapshot get(
            @PathVariable String runId,
            HttpServletRequest request) {
        long userId = AuthController.requireContext(request).userId();
        return runService.get(userId, organizationAccess.requireContext(userId).organizationId(), runId);
    }

    @GetMapping(value = "/{runId}/export", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "导出 Agent Trace", description = "下载当前 Run 的权威脱敏 Trace JSON。")
    public ResponseEntity<AgentRunSnapshot> export(@PathVariable String runId, HttpServletRequest request) {
        AgentRunSnapshot snapshot = get(runId, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=agent-trace-" + runId + ".json")
                .body(snapshot);
    }

    public record CreateAgentRunRequest(
            /* 可选工作项上下文。 */
            @Positive Long workItemId,
            /* 本轮允许的 Product 或 UX Skill。 */
            @NotNull AgentSkill skill,
            /* 本轮 MEDIUM 风险 Tool 的确认策略；缺省 ASK，非法取值由枚举反序列化拒绝。 */
            MediumToolConfirmation mediumToolConfirmation,
            /* 仅在请求内交给 Runner，不能原样持久化。 */
            @NotBlank @Size(max = 10000) String message,
            /* 同一用户和公司内的客户端幂等键。 */
            @NotBlank @Size(max = 100) String clientRequestId) {
    }
}
