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

    boolean start(long organizationId, String runId, String requestId);

    void cancel(long organizationId, String runId, String requestId);

    void complete(long organizationId, String runId, String requestId, String summary,
            List<String> plan, List<AgentRuntimeGateway.ToolCallResult> toolCalls);

    /* 持久化模型正文增量，使浏览器断线后仍可按 Run sequence 完整重放。 */
    void appendMessageDelta(long organizationId, String runId, String delta);

    /* 持久化可展示的计划摘要；不得写入模型内部私有推理文本。 */
    void appendReasoningDelta(long organizationId, String runId, String delta);

    /* create_requirement 成功后立即把来源会话绑定到新需求。 */
    void bindConversationToRequirement(long organizationId, String runId, long requirementId);

    void fail(long organizationId, String runId, String requestId, String errorCode);

    record CreateResult(
            /* 已创建或由幂等键命中的 Run。 */
            AgentRun run,
            /* 是否由本次调用首次插入。 */
            boolean created) {
    }
}
