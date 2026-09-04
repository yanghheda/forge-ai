import { describe, expect, it, vi } from "vitest";

import { getDeliveryGraph } from "./work-item-api";

describe("work item API", () => {
  it("按 Workspace、Project 和 Requirement 范围读取 Delivery Graph", async () => {
    const graph = { nodes: [], edges: [], truncated: false, maxDepth: 8, maxNodes: 500 };
    const client = { request: vi.fn().mockResolvedValue(graph) };

    await expect(getDeliveryGraph(10, 20, 30, client)).resolves.toEqual(graph);
    expect(client.request).toHaveBeenCalledWith(
      "/v1/work-items/30/delivery-graph?workspaceId=10&projectId=20",
    );
  });
});
