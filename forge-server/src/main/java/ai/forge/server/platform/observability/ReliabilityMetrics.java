package ai.forge.server.platform.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.function.LongSupplier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test-unit")
public class ReliabilityMetrics implements MeterBinder {

    /* 只读可靠性查询；每个指标均来自 MySQL 权威队列事实。 */
    private final ReliabilityMetricsMapper mapper;

    public ReliabilityMetrics(ReliabilityMetricsMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("forge.outbox.pending", this, ignored -> read(mapper::countPendingOutbox))
                .description("尚未被消费者确认完成的 Outbox 事件数")
                .register(registry);
        Gauge.builder("forge.outbox.oldest.age", this, ignored -> read(mapper::oldestPendingOutboxAgeSeconds))
                .baseUnit("seconds")
                .description("最老未处理 Outbox 事件的等待秒数")
                .register(registry);
        Gauge.builder("forge.document.index.dead", this, ignored -> read(mapper::countDeadDocumentIndexJobs))
                .description("需要人工处理的文档索引死信数")
                .register(registry);
        Gauge.builder("forge.webhook.dead", this, ignored -> read(mapper::countDeadWebhookDeliveries))
                .description("需要人工处理的 GitLab Webhook 死信数")
                .register(registry);
    }

    private double read(LongSupplier query) {
        try {
            return query.getAsLong();
        } catch (RuntimeException exception) {
            return Double.NaN;
        }
    }
}
