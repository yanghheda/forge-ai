package ai.forge.server.agent.application;

public interface AgentRuntimeGateway {

    RunResult start(AgentRunRequested requested);

    default void cancel(String runId, long organizationId) {
        // 测试替身和不可中断实现可以只依赖 Server 权威取消状态。
    }

    record RunResult(
            /* Agent 图返回的结构化终态。 */
            String status,
            /* 模型生成的可展示计划。 */
            java.util.List<String> plan,
            /* 模型生成的最终摘要。 */
            String answer,
            /* Runtime 返回的结构化 Tool 观察，用于 Backend Trace 投影。 */
            java.util.List<ToolCallResult> toolCalls,
            /* Agent Checkpoint 中的单调状态版本。 */
            int stateVersion) {
    }

    record ToolCallResult(
            /* Tool 稳定名称。 */ String toolName,
            /* Run 内稳定调用标识。 */ String toolCallId,
            /* SUCCEEDED、FAILED 或 REJECTED。 */ String status,
            /* 稳定错误码；成功时为空。 */ String errorCode) {
    }
}
