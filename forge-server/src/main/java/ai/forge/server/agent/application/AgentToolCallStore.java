package ai.forge.server.agent.application;

public interface AgentToolCallStore {

    /* 按 runId 与 toolCallId 组成的幂等键查找已成功调用及其冻结输入。 */
    java.util.Optional<StoredToolCall> findSuccessful(
            long organizationId, String runId, String idempotencyKey);

    /* 按 Run 与公司 scope 读取全部工具调用事实，按创建顺序升序，供轨迹聚合使用。 */
    java.util.List<PersistedCall> findByRun(long organizationId, String runId);

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

    record PersistedCall(
            /* Agent 侧生成的本次调用标识。 */
            String toolCallId,
            /* 被执行的 Tool 契约名称。 */
            String toolName,
            /* 被执行的 Tool 契约版本。 */
            int toolVersion,
            /* 契约声明的风险等级。 */
            String riskLevel,
            /* 调用状态。 */
            String status,
            /* 结构化输入参数 JSON 原文。 */
            String argumentsJson,
            /* 结构化结果 JSON 原文；未完成时为空。 */
            String resultJson,
            /* 需要人工审批时关联的审批标识；自动执行时为空。 */
            String approvalId) {
    }
}
