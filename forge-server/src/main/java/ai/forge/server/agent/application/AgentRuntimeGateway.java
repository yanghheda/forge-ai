package ai.forge.server.agent.application;

public interface AgentRuntimeGateway {

    RunResult start(AgentRunRequested requested);

    record RunResult(
            /* Agent 图返回的结构化终态。 */
            String status,
            /* Fake LLM 生成的可展示计划。 */
            java.util.List<String> plan,
            /* Fake LLM 生成的最终摘要。 */
            String answer,
            /* Agent Checkpoint 中的单调状态版本。 */
            int stateVersion) {
    }
}
