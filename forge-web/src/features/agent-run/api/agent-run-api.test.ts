import { describe, expect, it, vi } from "vitest";

import type { ConversationRun, RunTrace } from "./agent-run-api";
import { getRunTrace, listConversationRuns } from "./agent-run-api";

const conversationRun: ConversationRun = {
  runId: "01KTEST0000000000000000001",
  skill: "PRODUCT",
  workItemId: 12,
  status: "SUCCEEDED",
  terminal: true,
  startedAt: "2026-09-05T00:00:00Z",
  finishedAt: "2026-09-05T00:01:30Z",
  errorCode: null,
  lastSequence: 8,
  triggerMessage: "帮我生成 PRD",
  answer: "已生成 PRD",
};

const runTrace: RunTrace = {
  runId: conversationRun.runId,
  skill: "PRODUCT",
  workItemId: 12,
  status: "SUCCEEDED",
  terminal: true,
  startedAt: "2026-09-05T00:00:00Z",
  finishedAt: "2026-09-05T00:01:30Z",
  errorCode: null,
  lastSequence: 8,
  nodes: [
    { key: "seq:1", kind: "THINKING", title: "选择工具", status: null, sequence: 1, startedAt: null, finishedAt: null, detail: { text: "先看需求" } },
    { key: "seq:2", kind: "ANSWER", title: "回答", status: "SUCCEEDED", sequence: 2, startedAt: null, finishedAt: null, detail: { text: "已生成 PRD" } },
  ],
};

describe("会话 Run 列表接口", () => {
  it("按会话 ID 读取 Run 摘要列表并原样返回结构", async () => {
    const client = { request: vi.fn().mockResolvedValue([conversationRun]) };

    const result = await listConversationRuns(42, client);

    expect(client.request).toHaveBeenCalledWith("/v1/agent-conversations/42/runs");
    expect(result).toEqual([conversationRun]);
    expect(result[0]?.triggerMessage).toBe("帮我生成 PRD");
  });

  it("无 Run 的会话返回空数组", async () => {
    const client = { request: vi.fn().mockResolvedValue([]) };

    await expect(listConversationRuns(7, client)).resolves.toEqual([]);
  });
});

describe("Run 轨迹详情接口", () => {
  it("按 Run ID 读取轨迹详情并保留有序节点", async () => {
    const client = { request: vi.fn().mockResolvedValue(runTrace) };

    const result = await getRunTrace("01KTEST0000000000000000001", client);

    expect(client.request).toHaveBeenCalledWith("/v1/agent-runs/01KTEST0000000000000000001/trace");
    expect(result).toEqual(runTrace);
    expect(result.nodes.map((node) => node.kind)).toEqual(["THINKING", "ANSWER"]);
  });

  it("未结束的 Run 也返回当前已持久化节点", async () => {
    const pending: RunTrace = { ...runTrace, terminal: false, finishedAt: null, status: "RUNNING", nodes: [runTrace.nodes[0]!] };
    const client = { request: vi.fn().mockResolvedValue(pending) };

    const result = await getRunTrace("01KTEST0000000000000000002", client);

    expect(result.terminal).toBe(false);
    expect(result.nodes).toHaveLength(1);
  });
});
