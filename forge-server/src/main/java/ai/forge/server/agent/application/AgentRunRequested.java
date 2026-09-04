package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.MediumToolConfirmation;

public record AgentRunRequested(
        /* 等待 Agent Gateway 启动的 Run 标识。 */
        String runId,
        /* Run 所属工作区，用于内部写入再次约束 scope。 */
        long workspaceId,
        /* Run 所属项目，用于内部写入再次约束 scope。 */
        long projectId,
        /* 可选工作项上下文；为空表示仅限定到项目。 */
        Long workItemId,
        /* Backend 已认证且完成授权的发起用户。 */
        long userId,
        /* 本轮允许执行的 Product 或 UX Skill。 */
        String skill,
        /* 本轮 MEDIUM 风险 Tool 的确认策略，随 Manifest 下发给 Agent。 */
        MediumToolConfirmation mediumToolConfirmation,
        /* 只跨内部网络交给模型的用户请求，不写入 Backend 业务表。 */
        String message,
        /* 创建请求的关联标识。 */
        String requestId) {
}
