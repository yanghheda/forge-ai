package ai.forge.server.agent.controller;

import ai.forge.server.agent.application.AgentToolExecution;
import ai.forge.server.agent.application.AgentToolExecuteService;
import ai.forge.server.platform.agent.InternalRunTokenVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test-unit")
@RequestMapping("/internal/v1/tools")
public class InternalToolController {

    /* 执行契约防线后的 Tool 调用；scope 完全来自 Run 事实。 */
    private final AgentToolExecuteService executeService;

    /* 验证 forge-agent 回调携带的短时 run-scoped credential。 */
    private final InternalRunTokenVerifier runTokenVerifier;

    public InternalToolController(
            AgentToolExecuteService executeService, InternalRunTokenVerifier runTokenVerifier) {
        this.executeService = executeService;
        this.runTokenVerifier = runTokenVerifier;
    }

    @PostMapping("/{toolName}:execute")
    public ResponseEntity<AgentToolExecution> execute(
            @PathVariable String toolName,
            @Valid @RequestBody ExecuteToolRequest body,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = bearerToken(authorization);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        InternalRunTokenVerifier.RunCredential credential;
        try {
            credential = runTokenVerifier.verify(token);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AgentToolExecution execution = executeService.execute(
                credential.organizationId(),
                credential.runId(),
                toolName,
                body.toolCallId(),
                body.arguments());
        return ResponseEntity.ok(execution);
    }

    /* 凭据与本次调用绑定；缺失或格式错误都按未认证处理。 */
    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length());
    }

    public record ExecuteToolRequest(
            /* Agent 侧生成的本次调用标识；与 Run 组成幂等键。 */
            @NotBlank @Size(max = 64) String toolCallId,
            /* 通过契约 Schema 校验前允许为空对象的结构化参数。 */
            JsonNode arguments) {
    }
}
