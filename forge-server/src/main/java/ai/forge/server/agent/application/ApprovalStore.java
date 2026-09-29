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

    /* 按 Run 与公司 scope 读取全部审批事实，按创建时间升序，供轨迹聚合使用。 */
    List<Map<String, Object>> findByRunAscending(long organizationId, String runId);

    /* 按公司 scope 读取等待决策的审批摘要（含发起人显示名），按失效时间升序，排除本人发起。 */
    List<Map<String, Object>> findPendingByOrganization(long organizationId, long excludeUserId);
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
