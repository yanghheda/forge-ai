package ai.forge.server.workitem.application;

import java.util.List;

public record DeliveryGraph(
        /* 图中经过逐类授权后可见的节点，顺序稳定且根节点优先。 */
        List<Node> nodes,
        /* 两端均可见的交付关系边。 */
        List<Edge> edges,
        /* 因深度或节点上限未返回完整可见图时为 true。 */
        boolean truncated,
        /* 服务端实际采用的最大遍历深度。 */
        int maxDepth,
        /* 服务端实际采用的最大返回节点数。 */
        int maxNodes) {

    public record Node(
            /* 带类型前缀的响应内稳定节点标识。 */ String id,
            /* WORK_ITEM 或 DOCUMENT，用于客户端选择展示方式。 */ String kind,
            /* 数据库中的业务标识。 */ long resourceId,
            /* 节点的稳定业务类型。 */ String type,
            /* 面向用户的节点标题。 */ String title,
            /* Work Item 状态或文档状态。 */ String status,
            /* 从根 Requirement 计算的最短深度。 */ int depth) {}

    public record Edge(
            /* 响应内稳定边标识。 */ String id,
            /* 响应内的起点节点标识。 */ String source,
            /* 响应内的终点节点标识。 */ String target,
            /* PARENT、文档关联或 Work Item relation 类型。 */ String type) {}
}
