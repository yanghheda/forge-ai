package ai.forge.server.agent.controller;

import ai.forge.server.agent.application.AgentRunStore;
import ai.forge.server.platform.agent.InternalRunTokenVerifier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test-unit")
@RequestMapping("/internal/v1/agent-runs")
public class InternalAgentEventController {
    /* 将 Runtime 正文增量写入 Server 权威 Run 事件。 */
    private final AgentRunStore runStore;
    /* 验证并绑定 Runtime 回调的 Run 与公司范围。 */
    private final InternalRunTokenVerifier tokenVerifier;

    public InternalAgentEventController(AgentRunStore runStore, InternalRunTokenVerifier tokenVerifier) {
        this.runStore = runStore;
        this.tokenVerifier = tokenVerifier;
    }

    @PostMapping("/{runId}/message-deltas")
    public ResponseEntity<Void> append(
            @PathVariable String runId,
            @Valid @RequestBody MessageDeltaRequest body,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        InternalRunTokenVerifier.RunCredential credential;
        try {
            credential = tokenVerifier.verify(authorization.substring("Bearer ".length()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!runId.equals(credential.runId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        runStore.appendMessageDelta(credential.organizationId(), runId, body.delta());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{runId}/reasoning-deltas")
    public ResponseEntity<Void> appendReasoning(
            @PathVariable String runId,
            @Valid @RequestBody MessageDeltaRequest body,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        InternalRunTokenVerifier.RunCredential credential = credential(runId, authorization);
        if (credential == null) {
            return ResponseEntity.status(authorization == null || !authorization.startsWith("Bearer ")
                    ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN).build();
        }
        runStore.appendReasoningDelta(credential.organizationId(), runId, body.delta());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{runId}/thinking-deltas")
    public ResponseEntity<Void> appendThinking(
            @PathVariable String runId,
            @Valid @RequestBody ThinkingDeltaRequest body,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        InternalRunTokenVerifier.RunCredential credential = credential(runId, authorization);
        if (credential == null) {
            return ResponseEntity.status(authorization == null || !authorization.startsWith("Bearer ")
                    ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN).build();
        }
        runStore.appendThinkingDelta(credential.organizationId(), runId, body.delta(), body.node());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{runId}/tool-events")
    public ResponseEntity<Void> appendToolEvent(
            @PathVariable String runId,
            @Valid @RequestBody ToolEventRequest body,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        /* 凭据缺失、非 Bearer 前缀或签名/有效期非法一律 401；仅当凭据合法但 Run 不匹配时 403。 */
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        InternalRunTokenVerifier.RunCredential credential;
        try {
            credential = tokenVerifier.verify(authorization.substring("Bearer ".length()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!runId.equals(credential.runId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        runStore.appendToolEvent(credential.organizationId(), runId, body.callId(), body.toolName(),
                body.event(), body.status(), body.summary(), body.durationMs());
        return ResponseEntity.noContent().build();
    }

    private InternalRunTokenVerifier.RunCredential credential(String runId, String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        try {
            InternalRunTokenVerifier.RunCredential credential = tokenVerifier.verify(
                    authorization.substring("Bearer ".length()));
            return runId.equals(credential.runId()) ? credential : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public record MessageDeltaRequest(
            /* 模型本次生成的正文片段；仅属于绑定 Run。 */
            @NotEmpty @Size(max = 4000) String delta) {
    }

    public record ThinkingDeltaRequest(
            /* 模型本次生成的思考文本片段；仅属于绑定 Run，单条不超过 4000 字符。 */
            @NotEmpty @Size(max = 4000) String delta,
            /* 思考来源的 Agent 图节点标识；用于轨迹页按节点聚合展示，最多 64 字符。 */
            @NotBlank @Size(max = 64) String node) {
    }

    public record ToolEventRequest(
            /* Agent 侧生成的本次调用标识；轨迹聚合据此把同一工具调用的多个事件合并为一个节点。 */
            @NotBlank @Size(max = 64) String callId,
            /* Tool 契约名称；用于轨迹页展示工具名称。 */
            @NotBlank @Size(max = 64) String toolName,
            /* 工具事件类型；仅允许 tool.started / tool.completed / tool.failed，非法值由校验拒绝。 */
            @NotBlank @Size(max = 32)
            @Pattern(regexp = "tool\\.(started|completed|failed)") String event,
            /* 事件对应的调用状态快照，例如 RUNNING / SUCCEEDED / FAILED。 */
            @NotBlank @Size(max = 32) String status,
            /* 工具执行摘要；可为空，最多 500 字符。 */
            @Size(max = 500) String summary,
            /* 工具执行耗时（毫秒）；可为空，存在时必须非负。 */
            @PositiveOrZero Long durationMs) {
    }
}
