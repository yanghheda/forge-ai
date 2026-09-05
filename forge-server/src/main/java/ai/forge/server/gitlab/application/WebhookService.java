package ai.forge.server.gitlab.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class WebhookService {

    /* Webhook Secret 解密与 delivery 去重写入端口。 */
    private final WebhookStore store;

    /* fallback delivery key 所需的最小 JSON 字段解析器。 */
    private final WebhookParser parser;

    /* 接收端允许进入内存与数据库短期列的最大字节数。 */
    private final int maxPayloadBytes;

    public WebhookService(
            WebhookStore store,
            WebhookParser parser,
            @Value("${forge.gitlab.webhook.max-payload-bytes:262144}") int maxPayloadBytes) {
        this.store = store;
        this.parser = parser;
        this.maxPayloadBytes = maxPayloadBytes;
    }

    public Acceptance accept(long connectionId, String eventType, String remoteDeliveryId,
            String presentedSecret, byte[] payload) {
        if (payload.length > maxPayloadBytes) {
            throw new WebhookRejectedException(413);
        }
        String expected = store.findSecret(connectionId).orElseThrow(() -> new WebhookRejectedException(401));
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), safe(presentedSecret).getBytes(StandardCharsets.UTF_8))) {
            throw new WebhookRejectedException(401);
        }
        String normalizedType = requireText(eventType);
        String body = new String(payload, StandardCharsets.UTF_8);
        String payloadHash = sha256(payload);
        String deliveryKey = remoteDeliveryId == null || remoteDeliveryId.isBlank()
                ? sha256((connectionId + "\n" + normalizedType + "\n" + parser.objectId(body) + "\n"
                        + parser.updatedAt(body) + "\n" + payloadHash).getBytes(StandardCharsets.UTF_8))
                : remoteDeliveryId.trim();
        boolean accepted = store.accept(connectionId, deliveryKey, normalizedType, payloadHash, body);
        return new Acceptance(deliveryKey, !accepted);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("invalid event type");
        }
        return value.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record Acceptance(
            /* 当前请求解析得到的稳定 delivery key。 */ String deliveryKey,
            /* delivery 是否此前已被接收。 */ boolean duplicate) {}
}
