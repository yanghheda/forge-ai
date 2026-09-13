import { describe, expect, it, vi } from "vitest";

import { completeDevTask, createDevTask, createTestCase, createTestRun, getDeliveryGraph, getDevelopmentSummary, getLatestTestRun, listBugs, listTestCases, startDevelopment, updateTestResult } from "./work-item-api";

describe("work item API", () => {
  it("按 Organization、Project 和 Requirement 范围读取 Delivery Graph", async () => {
    const graph = { nodes: [], edges: [], truncated: false, maxDepth: 8, maxNodes: 500 };
    const client = { request: vi.fn().mockResolvedValue(graph) };

    await expect(getDeliveryGraph(30, client)).resolves.toEqual(graph);
    expect(client.request).toHaveBeenCalledWith("/v1/work-items/30/delivery-graph");
  });

  it("创建 Dev Task 并以稳定幂等键启动开发", async () => {
    const client = { request: vi.fn().mockResolvedValue({ id: 40 }) };

    await createDevTask({ organizationId: 10, requirementId: 30, title: "API", description: "实现" }, client);
    await startDevelopment({ organizationId: 10, taskId: 40, idempotencyKey: "stable-1" }, client);

    expect(client.request).toHaveBeenNthCalledWith(1, "/v1/development/requirements/30/tasks", expect.objectContaining({ method: "POST" }));
    expect(client.request).toHaveBeenNthCalledWith(2, "/v1/development/tasks/40/start", expect.objectContaining({ method: "POST" }));
  });

  it("读取开发汇总并用任务版本完成 Dev Task", async () => {
    const client = { request: vi.fn().mockResolvedValue({ tasks: [] }) };

    await getDevelopmentSummary(10, 30, client);
    await completeDevTask({ organizationId: 10, taskId: 40, expectedVersion: 3 }, client);

    expect(client.request).toHaveBeenNthCalledWith(1, "/v1/development/requirements/30");
    expect(client.request).toHaveBeenNthCalledWith(2, "/v1/development/tasks/40/complete", expect.objectContaining({ method: "POST" }));
  });

  it("按 Requirement 读取真实测试用例、最新执行与 Bug", async () => {
    const client = { request: vi.fn().mockResolvedValue([]) };

    await listTestCases(0, 30, client);
    await getLatestTestRun(0, 30, client);
    await listBugs(0, 30, client);

    expect(client.request).toHaveBeenNthCalledWith(1, "/v1/qa/requirements/30/cases");
    expect(client.request).toHaveBeenNthCalledWith(2, "/v1/qa/requirements/30/runs/latest");
    expect(client.request).toHaveBeenNthCalledWith(3, "/v1/bugs?requirementId=30");
  });

  it("创建用例与执行，并将人工测试结果写回 Server", async () => {
    const client = { request: vi.fn().mockResolvedValue({ id: 1 }) };

    await createTestCase({ organizationId: 0, requirementId: 30, title: "登录", preconditions: "已注册", steps: ["登录"], expectedResult: "成功", priority: "P0", caseType: "REGRESSION" }, client);
    await createTestRun({ organizationId: 0, requirementId: 30, environment: "staging" }, client);
    await updateTestResult({ organizationId: 0, runId: 9, resultId: 8, status: "PASS", expectedVersion: 2 }, client);

    expect(client.request).toHaveBeenNthCalledWith(1, "/v1/qa/requirements/30/cases", expect.objectContaining({ method: "POST" }));
    expect(client.request).toHaveBeenNthCalledWith(2, "/v1/qa/requirements/30/runs", expect.objectContaining({ method: "POST" }));
    expect(client.request).toHaveBeenNthCalledWith(3, "/v1/qa/runs/9/results/8", expect.objectContaining({ method: "PUT" }));
  });
});
