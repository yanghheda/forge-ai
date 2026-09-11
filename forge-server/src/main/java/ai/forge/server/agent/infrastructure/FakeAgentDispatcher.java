package ai.forge.server.agent.infrastructure;

import ai.forge.server.agent.application.AgentRunRequested;
import ai.forge.server.agent.application.AgentRunStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Profile("!test-unit")
@ConditionalOnProperty(name = "forge.infrastructure.agent.dispatch-enabled", havingValue = "false")
public class FakeAgentDispatcher {

    /* 仅为 Server 基础设施测试保留，不参与任何真实运行环境。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(FakeAgentDispatcher.class);

    /* 通过事务方法推进 Run 并写入持久 Trace。 */
    private final AgentRunStore runStore;

    public FakeAgentDispatcher(AgentRunStore runStore) {
        this.runStore = runStore;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(AgentRunRequested requested) {
        try {
            runStore.start(
                    requested.organizationId(), requested.runId(), requested.requestId());
            runStore.complete(
                    requested.organizationId(),
                    requested.runId(),
                    requested.requestId(),
                    "Fake runner completed without LLM");
        } catch (RuntimeException exception) {
            LOGGER.error("Fake Agent Run failed: runId={}", requested.runId(), exception);
            runStore.fail(
                    requested.organizationId(),
                    requested.runId(),
                    requested.requestId(),
                    "FAKE_RUNNER_FAILED");
        }
    }
}
