package ai.forge.server.agent.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ApprovalStore {
    void insert(String id, long organizationId, String runId, String toolCallId,
            String riskLevel, long requestedBy, String toolName, int toolVersion, String argumentHash,
            String encryptedArguments, String resourceVersionsJson, String reason, LocalDateTime expiresAt);
    List<Map<String, Object>> find(long organizationId, String approvalId);
    List<Map<String, Object>> findByCall(long organizationId, String runId, String toolCallId);
    List<Map<String, Object>> findByRun(long organizationId, String runId);
    void insertWaitingToolCall(long organizationId, String runId, String toolCallId,
            String toolName, int toolVersion, String riskLevel, String argumentHash, String argumentsJson,
            String idempotencyKey, String approvalId);
    boolean markRunWaiting(long organizationId, String runId);
    void insertRequiredEvent(long organizationId, String runId, String approvalId,
            String toolName, String requestId);
    void expire(long organizationId, String approvalId);
    boolean decide(long organizationId, String approvalId, long approverId,
            String status, long expectedVersion);
    void insertResumeOutbox(long organizationId, String runId, String approvalId);
    void markRunResuming(long organizationId, String runId);
    void insertDecisionEvent(long organizationId, String runId, String approvalId,
            String eventType, String status, String requestId);
    void completeToolCall(long organizationId, String runId, String toolCallId, String resultJson);
}
