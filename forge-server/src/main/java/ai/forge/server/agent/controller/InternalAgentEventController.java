package ai.forge.server.agent.controller;

import ai.forge.server.agent.application.AgentRunStore;
import ai.forge.server.platform.agent.InternalRunTokenVerifier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
}
