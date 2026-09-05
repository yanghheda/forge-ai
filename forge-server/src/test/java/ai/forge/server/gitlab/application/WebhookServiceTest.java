package ai.forge.server.gitlab.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WebhookServiceTest {

    private static final byte[] PAYLOAD = ("{\"project\":{\"id\":123},\"object_attributes\":"
            + "{\"id\":81,\"updated_at\":\"2026-09-06T10:00:00Z\"}}")
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsValidSecretAndTreatsRepeatedRemoteUuidAsDuplicate() {
        FakeStore store = new FakeStore();
        WebhookService service = service(store, 1024);

        WebhookService.Acceptance first = service.accept(
                21L, "Pipeline Hook", "uuid-1", "webhook-secret", PAYLOAD);
        WebhookService.Acceptance repeated = service.accept(
                21L, "Pipeline Hook", "uuid-1", "webhook-secret", PAYLOAD);

        assertThat(first.duplicate()).isFalse();
        assertThat(repeated.duplicate()).isTrue();
        assertThat(store.keys).containsExactly("uuid-1");
    }

    @Test
    void computesStableFallbackKeyWithoutRemoteUuid() {
        WebhookService service = service(new FakeStore(), 1024);

        String first = service.accept(21L, "Pipeline Hook", null, "webhook-secret", PAYLOAD).deliveryKey();
        String second = service.accept(21L, "Pipeline Hook", " ", "webhook-secret", PAYLOAD).deliveryKey();

        assertThat(first).hasSize(64).isEqualTo(second);
    }

    @Test
    void rejectsInvalidSecretAndOversizedPayloadBeforePersistence() {
        FakeStore store = new FakeStore();
        WebhookService service = service(store, 8);

        assertThatThrownBy(() -> service.accept(21L, "Pipeline Hook", "uuid", "wrong", new byte[0]))
                .isInstanceOf(WebhookRejectedException.class)
                .extracting(exception -> ((WebhookRejectedException) exception).status())
                .isEqualTo(401);
        assertThatThrownBy(() -> service.accept(21L, "Pipeline Hook", "uuid", "webhook-secret", PAYLOAD))
                .isInstanceOf(WebhookRejectedException.class)
                .extracting(exception -> ((WebhookRejectedException) exception).status())
                .isEqualTo(413);
        assertThat(store.keys).isEmpty();
    }

    private static WebhookService service(FakeStore store, int maxBytes) {
        return new WebhookService(store, new WebhookParser(new ObjectMapper()), maxBytes);
    }

    private static final class FakeStore implements WebhookStore {
        private final Set<String> keys = new HashSet<>();

        @Override
        public Optional<String> findSecret(long connectionId) {
            return Optional.of("webhook-secret");
        }

        @Override
        public boolean accept(long connectionId, String deliveryKey, String eventType,
                String payloadHash, String payload) {
            return keys.add(deliveryKey);
        }

        @Override
        public Optional<WebhookDelivery> claimNext(Duration lease) {
            return Optional.empty();
        }

        @Override
        public void process(WebhookDelivery delivery, WebhookChange change) {}

        @Override
        public void ignore(WebhookDelivery delivery) {}

        @Override
        public void fail(WebhookDelivery delivery, String errorMessage, int maxAttempts, Duration backoff) {}
    }
}
