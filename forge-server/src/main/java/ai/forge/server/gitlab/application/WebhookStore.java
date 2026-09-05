package ai.forge.server.gitlab.application;

import java.time.Duration;
import java.util.Optional;

public interface WebhookStore {

    Optional<String> findSecret(long connectionId);

    boolean accept(long connectionId, String deliveryKey, String eventType, String payloadHash, String payload);

    Optional<WebhookDelivery> claimNext(Duration lease);

    void process(WebhookDelivery delivery, WebhookChange change);

    void ignore(WebhookDelivery delivery);

    void fail(WebhookDelivery delivery, String errorMessage, int maxAttempts, Duration backoff);
}
