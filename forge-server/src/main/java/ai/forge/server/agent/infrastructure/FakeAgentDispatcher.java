package ai.forge.server.agent.infrastructure;

import ai.forge.server.agent.application.AgentRunRequested;
import ai.forge.server.agent.application.AgentRunStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Profile("!test-unit")
public class FakeAgentDispatcher {

    /* 记录 Fake Runner 自身故障，Run 失败正文不暴露内部异常。 */
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
            runStore.startFake(
                    requested.workspaceId(), requested.projectId(), requested.runId(), requested.requestId());
            runStore.completeFake(
                    requested.workspaceId(), requested.projectId(), requested.runId(), requested.requestId());
        } catch (RuntimeException exception) {
            LOGGER.error("Fake Agent Run failed: runId={}", requested.runId(), exception);
            runStore.failFake(
                    requested.workspaceId(),
                    requested.projectId(),
                    requested.runId(),
                    requested.requestId(),
                    "FAKE_RUNNER_FAILED");
        }
    }
}
