package ai.forge.server.agent.infrastructure;

import ai.forge.server.agent.application.AgentRunRequested;
import ai.forge.server.agent.application.AgentRunStore;
import ai.forge.server.agent.application.AgentRuntimeGateway;
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
@ConditionalOnProperty(name = "forge.infrastructure.agent.dispatch-enabled", matchIfMissing = true)
public class AgentDispatcher {

    /* 记录跨进程调度故障，Run 事件只暴露稳定错误码。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentDispatcher.class);

    /* 调用真实 FastAPI 内部端点。 */
    private final AgentRuntimeGateway runtimeGateway;

    /* 将 Agent 结果投影为 Backend 权威 Run、Step 与 Event。 */
    private final AgentRunStore runStore;

    public AgentDispatcher(AgentRuntimeGateway runtimeGateway, AgentRunStore runStore) {
        this.runtimeGateway = runtimeGateway;
        this.runStore = runStore;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(AgentRunRequested requested) {
        try {
            runStore.start(
                    requested.organizationId(), requested.runId(), requested.requestId());
            AgentRuntimeGateway.RunResult result = runtimeGateway.start(requested);
            if ("WAITING_APPROVAL".equals(result.status())) {
                LOGGER.info("Agent Run paused for approval: runId={}", requested.runId());
                return;
            }
            runStore.complete(
                    requested.organizationId(),
                    requested.runId(),
                    requested.requestId(),
                    result.answer());
            LOGGER.info(
                    "Agent Run completed: runId={}, checkpointVersion={}",
                    requested.runId(),
                    result.stateVersion());
        } catch (RuntimeException exception) {
            LOGGER.error("Agent Run dispatch failed: runId={}", requested.runId(), exception);
            runStore.fail(
                    requested.organizationId(),
                    requested.runId(),
                    requested.requestId(),
                    "AGENT_GATEWAY_FAILED");
        }
    }
}
