package ai.forge.server.gitlab.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.gitlab.application.WebhookChange;
import ai.forge.server.gitlab.application.WebhookDelivery;
import ai.forge.server.gitlab.application.WebhookParser;
import ai.forge.server.gitlab.application.WebhookStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebhookProcessorTest {

    @Test
    void mapsPipelineEventAndDelegatesAtomicSnapshotAndOutboxWrite() {
        FakeStore store = new FakeStore(delivery("Pipeline Hook", pipelineJson()));
        WebhookProcessor processor = processor(store);

        assertThat(processor.processNext()).isTrue();
        assertThat(store.change).isInstanceOf(WebhookChange.PipelineChanged.class);
        WebhookChange.PipelineChanged changed = (WebhookChange.PipelineChanged) store.change;
        assertThat(changed.remotePipelineId()).isEqualTo(81L);
        assertThat(changed.status()).isEqualTo("success");
        assertThat(processor.processNext()).isFalse();
    }

    @Test
    void safelyAcknowledgesUnknownEventWithoutDomainChange() {
        FakeStore store = new FakeStore(delivery("Wiki Page Hook", "{}"));

        processor(store).processNext();

        assertThat(store.ignored).isTrue();
        assertThat(store.change).isNull();
    }

    @Test
    void mapsMergeRequestEventIntoNormalizedDomainChange() {
        String payload = "{\"project\":{\"id\":123},\"user\":{\"id\":8},\"object_attributes\":{"
                + "\"iid\":44,\"title\":\"Implement API\",\"source_branch\":\"feature/api\","
                + "\"target_branch\":\"main\",\"state\":\"opened\","
                + "\"url\":\"https://gitlab.example/mr/44\",\"merge_status\":\"can_be_merged\","
                + "\"last_commit\":{\"id\":\"abc\"},"
                + "\"updated_at\":\"2026-09-06T10:00:00Z\"}}";
        FakeStore store = new FakeStore(delivery("Merge Request Hook", payload));

        processor(store).processNext();

        WebhookChange.MergeRequestChanged changed = (WebhookChange.MergeRequestChanged) store.change;
        assertThat(changed.remoteMrIid()).isEqualTo(44L);
        assertThat(changed.headSha()).isEqualTo("abc");
        assertThat(changed.state()).isEqualTo("opened");
    }

    @Test
    void invalidKnownPayloadIsRetriedWithBackoff() {
        FakeStore store = new FakeStore(delivery("Pipeline Hook", "{}"));

        processor(store).processNext();

        assertThat(store.failed).isTrue();
        assertThat(store.backoff).isEqualTo(Duration.ofSeconds(10));
        assertThat(store.maxAttempts).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "failed", "canceled"})
    void preservesTerminalPipelineStatuses(String status) {
        FakeStore store = new FakeStore(delivery("Pipeline Hook", pipelineJson().replace("success", status)));

        processor(store).processNext();

        assertThat(((WebhookChange.PipelineChanged) store.change).status()).isEqualTo(status);
    }

    private static WebhookProcessor processor(FakeStore store) {
        return new WebhookProcessor(store, new WebhookParser(new ObjectMapper()),
                Duration.ofMinutes(1), 5, Duration.ofSeconds(5));
    }

    private static WebhookDelivery delivery(String type, String payload) {
        return new WebhookDelivery(1L, 7L, 21L, "uuid", type, "hash", payload, 1, LocalDateTime.now());
    }

    private static String pipelineJson() {
        return "{\"project\":{\"id\":123},\"object_attributes\":{"
                + "\"id\":81,\"ref\":\"main\",\"sha\":\"abc\",\"status\":\"success\","
                + "\"url\":\"https://gitlab.example/pipelines/81\","
                + "\"started_at\":\"2026-09-06T09:59:00Z\",\"finished_at\":\"2026-09-06T10:00:00Z\","
                + "\"updated_at\":\"2026-09-06T10:00:00Z\"}}";
    }

    private static final class FakeStore implements WebhookStore {
        private WebhookDelivery next;
        private WebhookChange change;
        private boolean ignored;
        private boolean failed;
        private Duration backoff;
        private int maxAttempts;

        private FakeStore(WebhookDelivery next) {
            this.next = next;
        }

        @Override
        public Optional<String> findSecret(long connectionId) {
            return Optional.empty();
        }

        @Override
        public boolean accept(long connectionId, String deliveryKey, String eventType,
                String payloadHash, String payload) {
            return false;
        }

        @Override
        public Optional<WebhookDelivery> claimNext(Duration lease) {
            WebhookDelivery claimed = next;
            next = null;
            return Optional.ofNullable(claimed);
        }

        @Override
        public void process(WebhookDelivery delivery, WebhookChange change) {
            this.change = change;
        }

        @Override
        public void ignore(WebhookDelivery delivery) {
            ignored = true;
        }

        @Override
        public void fail(WebhookDelivery delivery, String errorMessage, int maxAttempts, Duration backoff) {
            failed = true;
            this.maxAttempts = maxAttempts;
            this.backoff = backoff;
        }
    }
}
