package ai.forge.server.agent.application;

import ai.forge.server.agent.application.AgentTraceAggregator.NodeKind;
import java.time.Instant;
import java.util.Map;

public record TraceNode(
        /* 轨迹内稳定键；事件节点为 seq:<序号>，独立补充节点使用来源标识。 */
        String key,
        /* 节点类型，前端按此值分派渲染，未知值走通用渲染。 */
        NodeKind kind,
        /* 便于阅读的节点标题。 */
        String title,
        /* 节点状态；无状态语义的节点为空。 */
        String status,
        /* 节点对应的事件序号；无事件的补充节点使用末尾排序序号。 */
        long sequence,
        /* 节点开始时间；未知时为空。 */
        Instant startedAt,
        /* 节点结束时间；未结束或未知时为空。 */
        Instant finishedAt,
        /* 按 kind 变化的键值详情，不包含未持久化字段。 */
        Map<String, Object> detail) {
}
