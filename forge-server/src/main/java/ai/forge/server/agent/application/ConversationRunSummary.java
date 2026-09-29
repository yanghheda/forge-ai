package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.AgentRunStatus;
import ai.forge.server.agent.domain.AgentSkill;
import java.time.Instant;

public record ConversationRunSummary(
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
        /* 该轮触发 Run 的用户指令正文；来自 agent_messages 的 USER 消息。 */
        String triggerMessage,
        /* 该轮 Agent 可见回答正文；尚未产生时为空。 */
        String answer) {
}
