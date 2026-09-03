package ai.forge.server.workitem.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.workitem.domain.GuardResult;
import ai.forge.server.workitem.domain.IdempotencyConflictException;
import ai.forge.server.workitem.domain.RequirementWorkflowRegistry;
import ai.forge.server.workitem.domain.TransitionContext;
import ai.forge.server.workitem.domain.TransitionDefinition;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemEvent;
import ai.forge.server.workitem.domain.WorkflowAction;
import ai.forge.server.workitem.domain.WorkflowGuardFailedException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test-unit")
public class RequirementTransitionService {

    /* 读取带租户与项目范围的 Work Item 当前事实。 */
    private final WorkItemStore workItemStore;

    /* 在资源加载后执行动作对应的最终权限检查。 */
    private final PermissionEvaluator permissionEvaluator;

    /* 将 Action 解析为服务端固定的 From、To、权限与 Guard。 */
    private final RequirementWorkflowRegistry workflowRegistry;

    /* 原子写入状态、评审记录和追加活动事件。 */
    private final RequirementTransitionStore transitionStore;

    public RequirementTransitionService(
            WorkItemStore workItemStore,
            PermissionEvaluator permissionEvaluator,
            RequirementWorkflowRegistry workflowRegistry,
            RequirementTransitionStore transitionStore) {
        this.workItemStore = workItemStore;
        this.permissionEvaluator = permissionEvaluator;
        this.workflowRegistry = workflowRegistry;
        this.transitionStore = transitionStore;
    }

    @Transactional
    public RequirementTransitionStore.TransitionResult transition(
            long userId,
            long workspaceId,
            long projectId,
            long workItemId,
            WorkflowAction action,
            long expectedVersion,
            String idempotencyKey,
            String reason) {
        WorkItem current = workItemStore.findByIdAndScope(workspaceId, projectId, workItemId)
                .orElseThrow(ResourceNotFoundException::new);
        Optional<RequirementTransitionStore.TransitionResult> previous =
                transitionStore.findResultByIdempotencyKey(workspaceId, projectId, workItemId, idempotencyKey);
        if (previous.isPresent()) {
            if (previous.get().action() != action) {
                throw new IdempotencyConflictException();
            }
            TransitionDefinition replayDefinition = workflowRegistry.requireAction(current.type(), action);
            permissionEvaluator.requireProject(
                    userId, workspaceId, projectId, replayDefinition.requiredPermission());
            return previous.get();
        }
        TransitionDefinition definition = workflowRegistry.require(current.type(), current.status(), action);
        permissionEvaluator.requireProject(userId, workspaceId, projectId, definition.requiredPermission());
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        TransitionContext context = new TransitionContext(current, normalizeReason(reason));
        for (var guard : definition.guards()) {
            GuardResult result = guard.evaluate(context);
            missing.addAll(result.missing());
        }
        if (!missing.isEmpty()) {
            throw new WorkflowGuardFailedException(List.copyOf(missing));
        }
        return transitionStore.transition(
                workspaceId,
                projectId,
                workItemId,
                userId,
                expectedVersion,
                idempotencyKey,
                context.reason(),
                definition);
    }

    public List<WorkItemEvent> events(
            long userId, long workspaceId, long projectId, long workItemId) {
        WorkItem item = workItemStore.findByIdAndScope(workspaceId, projectId, workItemId)
                .orElseThrow(ResourceNotFoundException::new);
        permissionEvaluator.requireProject(
                userId, workspaceId, projectId, item.type().permissionResource() + ".read");
        return transitionStore.findEvents(workspaceId, projectId, workItemId);
    }

    private String normalizeReason(String reason) {
        return reason == null ? null : reason.trim();
    }
}
