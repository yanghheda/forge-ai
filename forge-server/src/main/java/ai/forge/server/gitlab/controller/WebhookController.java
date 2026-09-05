package ai.forge.server.gitlab.controller;

import ai.forge.server.gitlab.application.WebhookService;
import ai.forge.server.gitlab.application.WebhookRejectedException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/gitlab/webhooks")
@Hidden
public class WebhookController {

    /* 仅负责验签、delivery key 和去重接收入库的快速入口。 */
    private final WebhookService service;

    /* 在消息转换器分配完整 body 前约束读取字节数。 */
    private final int maxPayloadBytes;

    public WebhookController(
            WebhookService service,
            @Value("${forge.gitlab.webhook.max-payload-bytes:262144}") int maxPayloadBytes) {
        this.service = service;
        this.maxPayloadBytes = maxPayloadBytes;
    }

    @PostMapping("/{connectionId}")
    public ResponseEntity<WebhookService.Acceptance> receive(
            @PathVariable long connectionId,
            @RequestHeader(name = "X-Gitlab-Event", required = false) String eventType,
            @RequestHeader(name = "X-Gitlab-Event-UUID", required = false) String deliveryId,
            @RequestHeader(name = "X-Gitlab-Token", required = false) String secret,
            HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > maxPayloadBytes) {
            throw new WebhookRejectedException(413);
        }
        byte[] payload = request.getInputStream().readNBytes(maxPayloadBytes + 1);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(service.accept(connectionId, eventType, deliveryId, secret, payload));
    }
}
