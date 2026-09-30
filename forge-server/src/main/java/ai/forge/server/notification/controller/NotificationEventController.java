package ai.forge.server.notification.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.notification.application.NotificationStreamService;
import ai.forge.server.organization.application.OrganizationAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/*
 * 用户级通知事件流端点；与既有 NotificationController 的读取端点路径不冲突，
 * 未登录按既有语义拒绝（由 AuthController.requireContext 抛出未认证异常）。
 */
@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification Event", description = "从 MySQL 补发并实时跟随当前用户的通知")
public class NotificationEventController {

    /* 建立与登录用户、公司范围绑定的 SSE 连接。 */
    private final NotificationStreamService streamService;
    /* 从登录成员解析唯一公司范围。 */ private final OrganizationAccessService organizationAccess;

    public NotificationEventController(
            NotificationStreamService streamService, OrganizationAccessService organizationAccess) {
        this.streamService = streamService;
        this.organizationAccess = organizationAccess;
    }

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "订阅通知事件", description = "支持 Last-Event-ID 或 afterId，从 MySQL 至少一次补发本人本公司通知。")
    public SseEmitter events(
            @RequestParam(required = false) Long afterId,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
            HttpServletRequest request) {
        long cursor = afterId == null ? parseLastEventId(lastEventId) : afterId;
        long userId = AuthController.requireContext(request).userId();
        return streamService.stream(userId, organizationAccess.requireContext(userId).organizationId(), cursor);
    }

    private long parseLastEventId(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(lastEventId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Last-Event-ID must be a notification id", exception);
        }
    }
}
