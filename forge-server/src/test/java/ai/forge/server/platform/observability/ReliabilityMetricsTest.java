package ai.forge.server.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ReliabilityMetricsTest {

    @Test
    void publishesQueueBacklogAgeAndDeadLetterFacts() {
        ReliabilityMetricsMapper mapper = Mockito.mock(ReliabilityMetricsMapper.class);
        when(mapper.countPendingOutbox()).thenReturn(7L);
        when(mapper.oldestPendingOutboxAgeSeconds()).thenReturn(361L);
        when(mapper.countDeadDocumentIndexJobs()).thenReturn(2L);
        when(mapper.countDeadWebhookDeliveries()).thenReturn(3L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new ReliabilityMetrics(mapper).bindTo(registry);

        assertThat(registry.get("forge.outbox.pending").gauge().value()).isEqualTo(7.0);
        assertThat(registry.get("forge.outbox.oldest.age").gauge().value()).isEqualTo(361.0);
        assertThat(registry.get("forge.document.index.dead").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("forge.webhook.dead").gauge().value()).isEqualTo(3.0);
    }

    @Test
    void databaseFailureIsUnknownInsteadOfFalseZero() {
        ReliabilityMetricsMapper mapper = Mockito.mock(ReliabilityMetricsMapper.class);
        when(mapper.countPendingOutbox()).thenThrow(new IllegalStateException("db unavailable"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new ReliabilityMetrics(mapper).bindTo(registry);

        assertThat(registry.get("forge.outbox.pending").gauge().value()).isNaN();
    }
}
