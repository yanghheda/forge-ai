package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.AgentRunStatus;
import ai.forge.server.agent.domain.AgentSkill;
import java.time.Instant;
import java.util.List;

public record AgentRunTrace(
        /* Run 的 ULID 标识。 */
        String runId,
        /* 本轮执行使用的角色 Skill。 */
        AgentSkill skill,
        /* 本轮绑定的 Requirement 标识；未绑定时为空。 */
        Long workItemId,
        /* Run 权威状态。 */
        AgentRunStatus status,
        /* 是否已进入终态。 */
        boolean terminal,
        /* Run 开始执行时间；排队期间为空。 */
        Instant startedAt,
        /* Run 进入终态时间；未结束时为空。 */
        Instant finishedAt,
        /* 稳定失败码；成功或未结束时为空。 */
        String errorCode,
        /* 已提交的最后事件序号。 */
        long lastSequence,
        /* 按事件序号升序排列的轨迹节点；未结束的 Run 仅含已持久化节点。 */
        List<TraceNode> nodes) {
}
