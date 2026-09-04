package ai.forge.server.agent.application;

public record AgentRunRequested(
        /* 等待 Fake dispatcher 启动的 Run 标识。 */
        String runId,
        /* Run 所属工作区，用于内部写入再次约束 scope。 */
        long workspaceId,
        /* Run 所属项目，用于内部写入再次约束 scope。 */
        long projectId,
        /* 创建请求的关联标识。 */
        String requestId) {
}
