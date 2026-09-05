package ai.forge.server.gitlab.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test-unit")
@ConditionalOnProperty(name = "forge.gitlab.webhook.schedule-enabled", matchIfMissing = true)
public class WebhookScheduler {

    /* 每次 tick 驱动的单 delivery Processor。 */
    private final WebhookProcessor processor;

    public WebhookScheduler(WebhookProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${forge.gitlab.webhook.poll-interval:1s}")
    public void tick() {
        while (processor.processNext()) {
            /* 每个 delivery 独立事务，避免一个批次长期占用数据库连接。 */
        }
    }
}
