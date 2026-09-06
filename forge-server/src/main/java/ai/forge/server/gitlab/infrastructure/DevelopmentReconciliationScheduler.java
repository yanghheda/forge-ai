package ai.forge.server.gitlab.infrastructure;

import ai.forge.server.gitlab.application.DevelopmentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test-unit")
@ConditionalOnProperty(name = "forge.gitlab.reconciliation.schedule-enabled", matchIfMissing = true)
public class DevelopmentReconciliationScheduler {

    /* 每次只恢复有限数量，避免 GitLab 故障时占满调度线程。 */
    private static final int MAX_OPERATIONS_PER_TICK = 20;

    /* 复用人工与 Agent 启动开发的同一幂等编排。 */
    private final DevelopmentService developmentService;

    public DevelopmentReconciliationScheduler(DevelopmentService developmentService) {
        this.developmentService = developmentService;
    }

    @Scheduled(fixedDelayString = "${forge.gitlab.reconciliation.poll-interval:30s}")
    public void tick() {
        for (int index = 0; index < MAX_OPERATIONS_PER_TICK; index++) {
            if (!developmentService.reconcileNext()) {
                return;
            }
        }
    }
}
