package ai.forge.server.agent.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ApprovalStore {
    void insert(String id, long workspaceId, long projectId, String runId, String toolCallId,
            String riskLevel, long requestedBy, String toolName, int toolVersion, String argumentHash,
            String encryptedArguments, String resourceVersionsJson, String reason, LocalDateTime expiresAt);
    List<Map<String, Object>> find(long workspaceId, long projectId, String approvalId);
    List<Map<String, Object>> findByCall(long workspaceId, long projectId, String runId, String toolCallId);
    List<Map<String, Object>> findByRun(long workspaceId, long projectId, String runId);
    void insertWaitingToolCall(long workspaceId, long projectId, String runId, String toolCallId,
            String toolName, int toolVersion, String riskLevel, String argumentHash, String argumentsJson,
            String idempotencyKey, String approvalId);
    boolean markRunWaiting(long workspaceId, long projectId, String runId);
    void insertRequiredEvent(long workspaceId, long projectId, String runId, String approvalId,
            String toolName, String requestId);
    void expire(long workspaceId, long projectId, String approvalId);
    boolean decide(long workspaceId, long projectId, String approvalId, long approverId,
            String status, long expectedVersion);
    void insertResumeOutbox(long workspaceId, long projectId, String runId, String approvalId);
    void markRunResuming(long workspaceId, long projectId, String runId);
    void insertDecisionEvent(long workspaceId, long projectId, String runId, String approvalId,
            String eventType, String status, String requestId);
    void completeToolCall(long workspaceId, long projectId, String runId, String toolCallId, String resultJson);
}
