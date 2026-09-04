package ai.forge.server.document.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test-unit")
@ConditionalOnProperty(name = "forge.rag.indexer.schedule-enabled", matchIfMissing = true)
public class DocumentIndexingScheduler {

    /* 被调度的索引 Worker；测试通过直接注入 Worker 手动驱动，不依赖定时器。 */
    private final DocumentIndexingWorker worker;

    public DocumentIndexingScheduler(DocumentIndexingWorker worker) {
        this.worker = worker;
    }

    /* 固定延迟轮询；Worker 内部逐任务处理并自带退避与租约语义。 */
    @Scheduled(fixedDelayString = "${forge.rag.indexer.poll-interval:5s}")
    public void tick() {
        worker.runOnce();
    }
}
