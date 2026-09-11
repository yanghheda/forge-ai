package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.AgentEvent;
import ai.forge.server.agent.domain.AgentRun;
import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.agent.domain.MediumToolConfirmation;
import java.util.List;
import java.util.Optional;

public interface AgentRunStore {

    CreateResult create(
            String runId,
            long organizationId,
            Long workItemId,
            long userId,
            AgentSkill skill,
            MediumToolConfirmation mediumToolConfirmation,
            String messageRedacted,
            String clientRequestId,
            String requestHash,
            String requestId);

    Optional<AgentRun> find(long organizationId, String runId);

    List<ai.forge.server.agent.domain.AgentStep> findSteps(
            long organizationId, String runId);

    List<AgentEvent> findEventsAfter(
            long organizationId, String runId, long afterSequence, int limit);

    void start(long organizationId, String runId, String requestId);

    void complete(long organizationId, String runId, String requestId, String summary);

    void fail(long organizationId, String runId, String requestId, String errorCode);

    record CreateResult(
            /* 已创建或由幂等键命中的 Run。 */
            AgentRun run,
            /* 是否由本次调用首次插入。 */
            boolean created) {
    }
}
