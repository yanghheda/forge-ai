package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.TransitionDefinition;
import ai.forge.server.workitem.domain.WorkItemEvent;
import ai.forge.server.workitem.domain.WorkflowAction;
import java.util.List;
import java.util.Optional;

public interface RequirementTransitionStore {

    Optional<TransitionResult> findResultByIdempotencyKey(
            long organizationId, long workItemId, String idempotencyKey);

    TransitionResult transition(
            long organizationId,
            long workItemId,
            long actorId,
            long expectedVersion,
            String idempotencyKey,
            String reason,
            TransitionDefinition definition,
            List<String> checklist);

    List<WorkItemEvent> findEvents(long organizationId, long workItemId);

    record TransitionResult(
            /* 成功执行或幂等重放的工作流动作。 */
            WorkflowAction action,
            /* 动作完成后的状态。 */
            ai.forge.server.workitem.domain.WorkItemStatus status,
            /* 动作完成后的聚合版本。 */
            long version,
            /* 对应该转换的活动事件标识。 */
            long eventId) {}
}
