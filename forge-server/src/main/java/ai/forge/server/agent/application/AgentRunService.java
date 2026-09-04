package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.AgentRun;
import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.agent.domain.MediumToolConfirmation;
import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.workitem.application.WorkItemStore;
import ai.forge.server.workitem.domain.WorkItem;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test-unit")
public class AgentRunService {

    /* 执行项目、Skill 与上下文资源授权。 */
    private final PermissionEvaluator permissionEvaluator;

    /* 校验可选工作项确实属于请求 scope。 */
    private final WorkItemStore workItemStore;

    /* 持久化 Run、Step 与 Event 权威事实。 */
    private final AgentRunStore runStore;

    /* 在创建事务提交后触发真实 Agent Gateway。 */
    private final ApplicationEventPublisher eventPublisher;

    public AgentRunService(
            PermissionEvaluator permissionEvaluator,
            WorkItemStore workItemStore,
            AgentRunStore runStore,
            ApplicationEventPublisher eventPublisher) {
        this.permissionEvaluator = permissionEvaluator;
        this.workItemStore = workItemStore;
        this.runStore = runStore;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AgentRunSnapshot create(
            long userId,
            long workspaceId,
            long projectId,
            Long workItemId,
            AgentSkill skill,
            MediumToolConfirmation mediumToolConfirmation,
            String message,
            String clientRequestId,
            String requestId) {
        permissionEvaluator.requireProject(userId, workspaceId, projectId, "agent.run");
        permissionEvaluator.requireProject(userId, workspaceId, projectId, skill.requiredPermission());
        requireWorkItemScope(userId, workspaceId, projectId, workItemId);
        MediumToolConfirmation policy = mediumToolConfirmation == null
                ? MediumToolConfirmation.ASK
                : mediumToolConfirmation;
        AgentRunStore.CreateResult result = runStore.create(
                AgentRunIdGenerator.next(),
                workspaceId,
                projectId,
                workItemId,
                userId,
                skill,
                policy,
                redact(message),
                normalizeClientRequestId(clientRequestId),
                AgentRequestFingerprint.create(skill, workItemId, message, policy),
                requestId);
        if (result.created()) {
            eventPublisher.publishEvent(new AgentRunRequested(
                    result.run().id(),
                    workspaceId,
                    projectId,
                    workItemId,
                    userId,
                    skill.name(),
                    policy,
                    message.trim(),
                    requestId));
        }
        return snapshot(workspaceId, projectId, result.run().id());
    }

    public AgentRunSnapshot get(long userId, long workspaceId, long projectId, String runId) {
        permissionEvaluator.requireProject(userId, workspaceId, projectId, "agent.run");
        return snapshot(workspaceId, projectId, normalizeRunId(runId));
    }

    public java.util.List<ai.forge.server.agent.domain.AgentEvent> eventsAfter(
            long userId, long workspaceId, long projectId, String runId, long afterSequence, int limit) {
        get(userId, workspaceId, projectId, runId);
        if (afterSequence < 0) {
            throw new IllegalArgumentException("afterSequence must not be negative");
        }
        return runStore.findEventsAfter(workspaceId, projectId, runId, afterSequence, limit);
    }

    private AgentRunSnapshot snapshot(long workspaceId, long projectId, String runId) {
        AgentRun run = runStore.find(workspaceId, projectId, runId).orElseThrow(ResourceNotFoundException::new);
        return new AgentRunSnapshot(
                run.id(),
                run.status(),
                run.lastSequence(),
                run.terminal(),
                run.startedAt(),
                run.finishedAt(),
                run.errorCode(),
                runStore.findSteps(workspaceId, projectId, runId));
    }

    private void requireWorkItemScope(long userId, long workspaceId, long projectId, Long workItemId) {
        if (workItemId == null) {
            return;
        }
        WorkItem item = workItemStore.findByIdAndScope(workspaceId, projectId, workItemId)
                .orElseThrow(ResourceNotFoundException::new);
        permissionEvaluator.requireProject(
                userId, workspaceId, projectId, item.type().permissionResource() + ".read");
    }

    private String redact(String message) {
        String normalized = message == null ? "" : message.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return "User request (" + normalized.length() + " chars, content redacted)";
    }

    private String normalizeClientRequestId(String clientRequestId) {
        String normalized = clientRequestId == null ? "" : clientRequestId.trim();
        if (!normalized.matches("[A-Za-z0-9._-]{1,100}")) {
            throw new IllegalArgumentException("invalid clientRequestId");
        }
        return normalized;
    }

    private String normalizeRunId(String runId) {
        if (runId == null || !runId.matches("[0-9A-HJKMNP-TV-Z]{26}")) {
            throw new ResourceNotFoundException();
        }
        return runId;
    }
}
