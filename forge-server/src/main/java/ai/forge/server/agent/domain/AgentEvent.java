package ai.forge.server.agent.domain;

import java.time.Instant;
import java.util.Map;

public record AgentEvent(
        /* 事件所属 Run。 */
        String runId,
        /* Run 内严格递增序号。 */
        long sequence,
        /* SSE 使用的稳定事件类型。 */
        String type,
        /* 事件产生时的请求关联标识。 */
        String requestId,
        /* UI 恢复所需的最小结构化载荷。 */
        Map<String, Object> payload,
        /* 事件提交时间。 */
        Instant timestamp) {
}
