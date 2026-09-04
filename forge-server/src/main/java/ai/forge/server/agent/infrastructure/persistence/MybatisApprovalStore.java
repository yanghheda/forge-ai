package ai.forge.server.agent.infrastructure.persistence;

import ai.forge.server.agent.application.ApprovalStore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test-unit")
public class MybatisApprovalStore implements ApprovalStore {
    /* 执行审批、Run、Event 与 Outbox 的显式 scope SQL。 */
    private final ApprovalMapper mapper;

    public MybatisApprovalStore(ApprovalMapper mapper) {
        this.mapper = mapper;
    }

    public void insert(String id, long workspaceId, long projectId, String runId, String toolCallId,
            String riskLevel, long requestedBy, String toolName, int toolVersion, String argumentHash,
            String encryptedArguments, String resourceVersionsJson, String reason, LocalDateTime expiresAt) {
        mapper.insert(id, workspaceId, projectId, runId, toolCallId, riskLevel, requestedBy, toolName,
                toolVersion, argumentHash, encryptedArguments, resourceVersionsJson, reason, expiresAt);
    }
    public List<Map<String, Object>> find(long workspaceId, long projectId, String approvalId) {
        return mapper.find(workspaceId, projectId, approvalId);
    }
    public List<Map<String, Object>> findByCall(long workspaceId, long projectId, String runId, String toolCallId) {
        return mapper.findByCall(workspaceId, projectId, runId, toolCallId);
    }
    public List<Map<String, Object>> findByRun(long workspaceId, long projectId, String runId) {
        return mapper.findByRun(workspaceId, projectId, runId);
    }
    public void insertWaitingToolCall(long workspaceId, long projectId, String runId, String toolCallId,
            String toolName, int toolVersion, String riskLevel, String argumentHash, String argumentsJson,
            String idempotencyKey, String approvalId) {
        mapper.insertWaitingToolCall(workspaceId, projectId, runId, toolCallId, toolName, toolVersion,
                riskLevel, argumentHash, argumentsJson, idempotencyKey, approvalId);
    }
    public boolean markRunWaiting(long workspaceId, long projectId, String runId) {
        return mapper.markRunWaiting(workspaceId, projectId, runId) == 1;
    }
    public void insertRequiredEvent(long workspaceId, long projectId, String runId, String approvalId,
            String toolName, String requestId) {
        mapper.insertRequiredEvent(workspaceId, projectId, runId, approvalId, toolName, requestId);
    }
    public void expire(long workspaceId, long projectId, String approvalId) {
        mapper.expire(workspaceId, projectId, approvalId);
    }
    public boolean decide(long workspaceId, long projectId, String approvalId, long approverId,
            String status, long expectedVersion) {
        return mapper.decide(workspaceId, projectId, approvalId, approverId, status, expectedVersion) == 1;
    }
    public void insertResumeOutbox(long workspaceId, long projectId, String runId, String approvalId) {
        mapper.insertResumeOutbox(workspaceId, projectId, runId, approvalId);
    }
    public void markRunResuming(long workspaceId, long projectId, String runId) {
        mapper.markRunResuming(workspaceId, projectId, runId);
    }
    public void insertDecisionEvent(long workspaceId, long projectId, String runId, String approvalId,
            String eventType, String status, String requestId) {
        mapper.insertDecisionEvent(workspaceId, projectId, runId, approvalId, eventType, status, requestId);
    }
    public void completeToolCall(long workspaceId, long projectId, String runId, String toolCallId,
            String resultJson) {
        mapper.completeToolCall(workspaceId, projectId, runId, toolCallId, resultJson);
    }
}
