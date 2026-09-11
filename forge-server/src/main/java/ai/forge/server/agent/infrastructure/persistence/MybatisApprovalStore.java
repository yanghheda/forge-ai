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

    public void insert(String id, long organizationId, String runId, String toolCallId,
            String riskLevel, long requestedBy, String toolName, int toolVersion, String argumentHash,
            String encryptedArguments, String resourceVersionsJson, String reason, LocalDateTime expiresAt) {
        mapper.insert(id, organizationId, runId, toolCallId, riskLevel, requestedBy, toolName,
                toolVersion, argumentHash, encryptedArguments, resourceVersionsJson, reason, expiresAt);
    }
    public List<Map<String, Object>> find(long organizationId, String approvalId) {
        return mapper.find(organizationId, approvalId);
    }
    public List<Map<String, Object>> findByCall(long organizationId, String runId, String toolCallId) {
        return mapper.findByCall(organizationId, runId, toolCallId);
    }
    public List<Map<String, Object>> findByRun(long organizationId, String runId) {
        return mapper.findByRun(organizationId, runId);
    }
    public void insertWaitingToolCall(long organizationId, String runId, String toolCallId,
            String toolName, int toolVersion, String riskLevel, String argumentHash, String argumentsJson,
            String idempotencyKey, String approvalId) {
        mapper.insertWaitingToolCall(organizationId, runId, toolCallId, toolName, toolVersion,
                riskLevel, argumentHash, argumentsJson, idempotencyKey, approvalId);
    }
    public boolean markRunWaiting(long organizationId, String runId) {
        return mapper.markRunWaiting(organizationId, runId) == 1;
    }
    public void insertRequiredEvent(long organizationId, String runId, String approvalId,
            String toolName, String requestId) {
        mapper.insertRequiredEvent(organizationId, runId, approvalId, toolName, requestId);
    }
    public void expire(long organizationId, String approvalId) {
        mapper.expire(organizationId, approvalId);
    }
    public boolean decide(long organizationId, String approvalId, long approverId,
            String status, long expectedVersion) {
        return mapper.decide(organizationId, approvalId, approverId, status, expectedVersion) == 1;
    }
    public void insertResumeOutbox(long organizationId, String runId, String approvalId) {
        mapper.insertResumeOutbox(organizationId, runId, approvalId);
    }
    public void markRunResuming(long organizationId, String runId) {
        mapper.markRunResuming(organizationId, runId);
    }
    public void insertDecisionEvent(long organizationId, String runId, String approvalId,
            String eventType, String status, String requestId) {
        mapper.insertDecisionEvent(organizationId, runId, approvalId, eventType, status, requestId);
    }
    public void completeToolCall(long organizationId, String runId, String toolCallId,
            String resultJson) {
        mapper.completeToolCall(organizationId, runId, toolCallId, resultJson);
    }
}
