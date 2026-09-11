package ai.forge.server.agent.application;

public interface AgentToolCallStore {

    /* 按 runId 与 toolCallId 组成的幂等键查找已成功调用及其冻结输入。 */
    java.util.Optional<StoredToolCall> findSuccessful(
            long organizationId, String runId, String idempotencyKey);

    /* 持久化一次成功执行的 MEDIUM 写操作；唯一键冲突表示重复副作用已被幂等键拦截。 */
    void record(
            long organizationId,
            String runId,
            String toolCallId,
            String toolName,
            int toolVersion,
            String riskLevel,
            String argumentsJson,
            String resultJson,
            String idempotencyKey);

    record StoredToolCall(
            /* 已提交调用使用的 Tool 名称。 */
            String toolName,
            /* 已提交调用使用的 Tool 契约版本。 */
            int toolVersion,
            /* 已提交调用通过校验后的结构化参数。 */
            String argumentsJson,
            /* 已提交调用返回的结构化结果。 */
            String resultJson) {
    }
}
