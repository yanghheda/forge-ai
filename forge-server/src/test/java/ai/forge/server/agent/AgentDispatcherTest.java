package ai.forge.server.agent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.forge.server.agent.application.AgentRunRequested;
import ai.forge.server.agent.application.AgentRunStore;
import ai.forge.server.agent.application.AgentRuntimeGateway;
import ai.forge.server.agent.domain.MediumToolConfirmation;
import ai.forge.server.agent.infrastructure.AgentDispatcher;
import ai.forge.server.agent.infrastructure.AgentRuntimeException;
import org.junit.jupiter.api.Test;

class AgentDispatcherTest {

    @Test
    void persistsStableRuntimeErrorInsteadOfGenericGatewayFailure() {
        AgentRuntimeGateway gateway = mock(AgentRuntimeGateway.class);
        AgentRunStore store = mock(AgentRunStore.class);
        AgentRunRequested requested = new AgentRunRequested(
                "01JTEST0000000000000000000",
                2,
                1024L,
                12,
                "RELEASE",
                MediumToolConfirmation.ASK,
                "运行预检",
                "request-1");
        when(store.start(2, requested.runId(), requested.requestId())).thenReturn(true);
        when(gateway.start(requested)).thenThrow(new AgentRuntimeException(
                "AGENT_MODEL_TIMEOUT", true, "model timed out", null));

        new AgentDispatcher(gateway, store).dispatch(requested);

        verify(store).fail(2, requested.runId(), requested.requestId(), "AGENT_MODEL_TIMEOUT");
    }
}
