"use client";

import { Alert, Card, Spin, Typography } from "@arco-design/web-react";
import { useQuery } from "@tanstack/react-query";
import { getDeliveryGraph, type DeliveryGraph as Graph } from "../api/work-item-api";

export function DeliveryGraphPanel({
  workspaceId,
  projectId,
  workItemId,
}: {
  workspaceId: number;
  projectId: number;
  workItemId: number;
}) {
  const graph = useQuery({
    queryKey: ["delivery-graph", workItemId],
    queryFn: () => getDeliveryGraph(workspaceId, projectId, workItemId),
  });
  if (graph.isPending) {
    return <Spin tip="加载 Delivery Graph…" />;
  }
  if (!graph.data) {
    return <Alert type="error" content="Delivery Graph 不存在或无权访问。" />;
  }
  return <DeliveryGraphView graph={graph.data} />;
}

export function DeliveryGraphView({ graph }: { graph: Graph }) {
  const positions = layout(graph);
  const useListOnly = graph.nodes.length > 80;
  return (
    <Card title="Delivery Graph">
      {graph.truncated && (
        <Alert
          type="warning"
          content={`结果已按深度 ${graph.maxDepth}、节点 ${graph.maxNodes} 的上限截断。`}
        />
      )}
      {!useListOnly && (
        <svg
          aria-label="交付关系图"
          role="img"
          viewBox={`0 0 ${Math.max(480, (maxDepth(graph) + 1) * 220)} ${Math.max(120, graph.nodes.length * 72)}`}
          style={{ width: "100%", minHeight: 180 }}
        >
          {graph.edges.map((edge) => {
            const source = positions.get(edge.source);
            const target = positions.get(edge.target);
            return source && target ? (
              <line
                key={edge.id}
                x1={source.x + 80}
                y1={source.y}
                x2={target.x - 80}
                y2={target.y}
                stroke="currentColor"
                strokeOpacity="0.35"
              />
            ) : null;
          })}
          {graph.nodes.map((node) => {
            const position = positions.get(node.id)!;
            return (
              <g key={node.id} transform={`translate(${position.x}, ${position.y})`}>
                <rect x="-78" y="-24" width="156" height="48" rx="8" fill="white" stroke="currentColor" />
                <text textAnchor="middle" y="-4" fontSize="11">{node.type}</text>
                <text textAnchor="middle" y="13" fontSize="10">{shorten(node.title)}</text>
              </g>
            );
          })}
        </svg>
      )}
      {useListOnly && (
        <Alert type="info" content="节点较多，已切换为列表视图以保持页面可用。" />
      )}
      <Typography.Title heading={6}>可访问交付节点</Typography.Title>
      <ol aria-label="交付节点列表">
        {graph.nodes.map((node) => (
          <li key={node.id} style={{ marginLeft: node.depth * 16 }}>
            {node.type} · {node.title} · {node.status}
          </li>
        ))}
      </ol>
    </Card>
  );
}

function layout(graph: Graph) {
  const depthRows = new Map<number, number>();
  return new Map(
    graph.nodes.map((node) => {
      const row = depthRows.get(node.depth) ?? 0;
      depthRows.set(node.depth, row + 1);
      return [node.id, { x: 110 + node.depth * 220, y: 50 + row * 72 }];
    }),
  );
}

function maxDepth(graph: Graph) {
  return graph.nodes.reduce((maximum, node) => Math.max(maximum, node.depth), 0);
}

function shorten(value: string) {
  return value.length > 22 ? `${value.slice(0, 21)}…` : value;
}
