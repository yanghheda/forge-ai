package ai.forge.server.agent.domain;

import java.time.Instant;

public record AgentRun(
        /* 服务端生成的 ULID Run 标识。 */
        String id,
        /* Run 所属工作区。 */
        long workspaceId,
        /* Run 所属项目。 */
        long projectId,
        /* 可选的工作项上下文。 */
        Long workItemId,
        /* 发起 Run 的用户。 */
        long userId,
        /* 本轮执行的 Skill。 */
        AgentSkill skill,
        /* 本轮 MEDIUM 风险 Tool 的确认策略；执行时按当前事实重新判定。 */
        MediumToolConfirmation mediumToolConfirmation,
        /* Backend 权威运行状态。 */
        AgentRunStatus status,
        /* 已持久化的最后事件序号。 */
        long lastSequence,
        /* Run 开始执行时间。 */
        Instant startedAt,
        /* Run 进入终态时间。 */
        Instant finishedAt,
        /* 稳定失败码。 */
        String errorCode,
        /* Run 创建时间。 */
        Instant createdAt) {

    public boolean terminal() {
        return status.terminal();
    }
}
