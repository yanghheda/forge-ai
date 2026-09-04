package ai.forge.server.agent.domain;

import java.time.Instant;

public record AgentStep(
        /* Run 内稳定步骤序号。 */
        int stepNo,
        /* 步骤类型。 */
        String type,
        /* 时间线展示名称。 */
        String name,
        /* 步骤当前状态。 */
        String status,
        /* 脱敏输出摘要。 */
        String outputSummary,
        /* 步骤开始时间。 */
        Instant startedAt,
        /* 步骤结束时间。 */
        Instant finishedAt) {
}
