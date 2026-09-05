package ai.forge.server.gitlab.infrastructure;

import ai.forge.server.gitlab.application.WebhookChange;
import ai.forge.server.gitlab.application.WebhookDelivery;
import ai.forge.server.gitlab.application.WebhookParser;
import ai.forge.server.gitlab.application.WebhookStore;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test-unit")
public class WebhookProcessor {

    /* 日志仅包含 delivery 标识和截断错误，不写入原始 payload。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookProcessor.class);

    /* delivery 领取、快照 upsert、Outbox 和失败退避的事务端口。 */
    private final WebhookStore store;

    /* GitLab 事件到有限领域变化类型的纯解析器。 */
    private final WebhookParser parser;

    /* PROCESSING 超过此时间可被其他实例重新领取。 */
    private final Duration lease;

    /* 达到此失败次数后 delivery 进入 DEAD。 */
    private final int maxAttempts;

    /* 首次失败后的退避基数，后续按二次幂增长。 */
    private final Duration backoffBase;

    public WebhookProcessor(
            WebhookStore store,
            WebhookParser parser,
            @Value("${forge.gitlab.webhook.lease:1m}") Duration lease,
            @Value("${forge.gitlab.webhook.max-attempts:5}") int maxAttempts,
            @Value("${forge.gitlab.webhook.backoff-base:5s}") Duration backoffBase) {
        this.store = store;
        this.parser = parser;
        this.lease = lease;
        this.maxAttempts = maxAttempts;
        this.backoffBase = backoffBase;
    }

    public boolean processNext() {
        WebhookDelivery delivery = store.claimNext(lease).orElse(null);
        if (delivery == null) {
            return false;
        }
        try {
            Optional<WebhookChange> change = parser.parse(delivery.eventType(), delivery.payload());
            if (change.isPresent()) {
                store.process(delivery, change.get());
            } else {
                store.ignore(delivery);
            }
        } catch (RuntimeException exception) {
            int exponent = Math.min(delivery.attempts(), 10);
            Duration backoff = backoffBase.multipliedBy(1L << exponent);
            store.fail(delivery, truncate(exception.getMessage()), maxAttempts, backoff);
            LOGGER.warn("webhook processing failed: deliveryId={}, reason={}", delivery.id(), truncate(exception.getMessage()));
        }
        return true;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "WEBHOOK_PROCESSING_FAILED";
        }
        return value.substring(0, Math.min(value.length(), 512));
    }
}
