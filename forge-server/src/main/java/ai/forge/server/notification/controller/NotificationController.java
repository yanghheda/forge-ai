package ai.forge.server.notification.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.notification.application.NotificationService;
import ai.forge.server.notification.application.NotificationView;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/*
 * 站内通知读取端点；路径、参数默认值与响应形状与迁移前 ConsoleController 保持一致。
 */
@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1")
public class NotificationController {
    /* 通知查询与已读命令。 */
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/notifications")
    public List<NotificationView> notifications(@RequestParam(defaultValue = "30") int limit,
            HttpServletRequest request) {
        return service.list(user(request), limit);
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unread(HttpServletRequest request) {
        return Map.of("count", service.unread(user(request)));
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> read(@PathVariable long id, HttpServletRequest request) {
        service.markRead(user(request), id);
        return ResponseEntity.noContent().build();
    }

    private long user(HttpServletRequest request) {
        return AuthController.requireContext(request).userId();
    }
}
