package ai.forge.server.agent.controller;

import ai.forge.server.agent.application.AgentEventStreamService;
import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.organization.application.OrganizationAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/agent-runs")
@Tag(name = "Agent Event", description = "从 MySQL 补发并实时跟随 Agent Run 持久事件")
public class AgentEventController {

    /* 建立与 Run 生命周期解耦的 scoped SSE 连接。 */
    private final AgentEventStreamService streamService;
    /* 从登录成员解析唯一公司范围。 */ private final OrganizationAccessService organizationAccess;

    public AgentEventController(AgentEventStreamService streamService, OrganizationAccessService organizationAccess) {
        this.streamService = streamService;
        this.organizationAccess = organizationAccess;
    }

    @GetMapping(path = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "订阅 Agent Run 事件", description = "支持 Last-Event-ID 或 afterSequence，从 MySQL 至少一次补发。")
    public SseEmitter events(
            @PathVariable String runId,
            @RequestParam(required = false) Long afterSequence,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
            HttpServletRequest request) {
        long cursor = afterSequence == null ? parseLastEventId(lastEventId) : afterSequence;
        long userId = AuthController.requireContext(request).userId();
        return streamService.stream(userId, organizationAccess.requireContext(userId).organizationId(), runId, cursor);
    }

    private long parseLastEventId(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(lastEventId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Last-Event-ID must be a sequence number", exception);
        }
    }
}
