package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.AgentRunStatus;
import ai.forge.server.agent.domain.AgentStep;
import java.time.Instant;
import java.util.List;

public record AgentRunSnapshot(
        /* Run 的 ULID 标识。 */
        String id,
        /* 本轮由服务端阶段路由确定的角色 Skill。 */
        ai.forge.server.agent.domain.AgentSkill skill,
        /* 本轮绑定的 Requirement；创建前的 Product 会话可为空。 */
        Long workItemId,
        /* Run 权威状态。 */
        AgentRunStatus status,
        /* 已提交的最后事件序号。 */
        long lastSequence,
        /* 是否已进入终态。 */
        boolean terminal,
        /* Run 开始时间。 */
        Instant startedAt,
        /* Run 结束时间。 */
        Instant finishedAt,
        /* 稳定失败码。 */
        String errorCode,
        /* 当前已提交的步骤 Trace。 */
        List<AgentStep> steps) {
}
