import { describe, expect, it, vi } from "vitest";

import {
  completeDevTask,
  createDevTask,
  getDeliveryGraph,
  getDevelopmentSummary,
  startDevelopment,
} from "./work-item-api";

describe("work item API", () => {
  it("按 Organization、Project 和 Requirement 范围读取 Delivery Graph", async () => {
    const graph = { nodes: [], edges: [], truncated: false, maxDepth: 8, maxNodes: 500 };
    const client = { request: vi.fn().mockResolvedValue(graph) };

    await expect(getDeliveryGraph(10, 20, 30, client)).resolves.toEqual(graph);
    expect(client.request).toHaveBeenCalledWith(
      "/v1/work-items/30/delivery-graph?organizationId=10&organizationId=20",
    );
  });

  it("创建 Dev Task 并以稳定幂等键启动开发", async () => {
    const client = { request: vi.fn().mockResolvedValue({ id: 40 }) };

    await createDevTask(
      { organizationId: 10, requirementId: 30, title: "API", description: "实现" },
      client,
    );
    await startDevelopment(
      { organizationId: 10, taskId: 40, idempotencyKey: "stable-1" },
      client,
    );

    expect(client.request).toHaveBeenNthCalledWith(
      1,
      "/v1/development/requirements/30/tasks",
      expect.objectContaining({ method: "POST" }),
    );
    expect(client.request).toHaveBeenNthCalledWith(
      2,
      "/v1/development/tasks/40/start",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("读取开发汇总并用任务版本完成 Dev Task", async () => {
    const client = { request: vi.fn().mockResolvedValue({ tasks: [] }) };

    await getDevelopmentSummary(10, 20, 30, client);
    await completeDevTask(
      { organizationId: 10, taskId: 40, expectedVersion: 3 },
      client,
    );

    expect(client.request).toHaveBeenNthCalledWith(
      1,
      "/v1/development/requirements/30?organizationId=10&organizationId=20",
    );
    expect(client.request).toHaveBeenNthCalledWith(
      2,
      "/v1/development/tasks/40/complete",
      expect.objectContaining({ method: "POST" }),
    );
  });
});
