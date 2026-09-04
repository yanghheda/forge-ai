package ai.forge.server.agent.application;

import com.fasterxml.jackson.databind.JsonNode;

/* 单次 Tool 执行返回给 forge-agent 的结构化信封；模型只能依据它解释结果。 */
public record AgentToolExecution(
        /* SUCCEEDED、PENDING_CONFIRMATION、REJECTED 或 FAILED。 */
        String status,
        /* 本次执行的 Tool 契约名称。 */
        String toolName,
        /* Agent 侧生成的本次调用标识。 */
        String toolCallId,
        /* 幂等键命中已成功副作用时为 true，表示结果来自重放。 */
        boolean replayed,
        /* 结构化业务结果；FAILED 时为空。 */
        JsonNode result,
        /* FAILED 时的稳定错误码，其余状态为空。 */
        String errorCode,
        /* FAILED 时的可读错误说明；不包含敏感数据。 */
        String errorMessage) {

    public static AgentToolExecution succeeded(
            String toolName, String toolCallId, boolean replayed, JsonNode result) {
        return new AgentToolExecution("SUCCEEDED", toolName, toolCallId, replayed, result, null, null);
    }

    public static AgentToolExecution pendingConfirmation(String toolName, String toolCallId) {
        return new AgentToolExecution(
                "PENDING_CONFIRMATION", toolName, toolCallId, false, null, null, null);
    }

    public static AgentToolExecution rejected(String toolName, String toolCallId, String reason) {
        return new AgentToolExecution("REJECTED", toolName, toolCallId, false, null, "MEDIUM_DENIED", reason);
    }

    public static AgentToolExecution failed(
            String toolName, String toolCallId, String errorCode, String errorMessage) {
        return new AgentToolExecution(
                "FAILED", toolName, toolCallId, false, null, errorCode, errorMessage);
    }
}
