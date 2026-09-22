import { describe, expect, it, vi } from "vitest";

import { deleteRequirement, updateWorkItem } from "./work-item-api";

describe("work item update API", () => {
  it("使用聚合版本更新需求描述", async () => {
    const client = { request: vi.fn().mockResolvedValue({ id: 30, version: 4 }) };

    await updateWorkItem(30, { description: "补充后的需求描述", expectedVersion: 3 }, client);

    expect(client.request).toHaveBeenCalledWith(
      "/v1/work-items/30",
      expect.objectContaining({
        method: "PATCH",
        body: JSON.stringify({ description: "补充后的需求描述", expectedVersion: 3 }),
      }),
    );
  });

  it("仅提交本次变更的优先级字段", async () => {
    const client = { request: vi.fn().mockResolvedValue({ id: 30, version: 4 }) };

    await updateWorkItem(30, { priority: "HIGH", expectedVersion: 3 }, client);

    expect(client.request).toHaveBeenCalledWith(
      "/v1/work-items/30",
      expect.objectContaining({
        method: "PATCH",
        body: JSON.stringify({ priority: "HIGH", expectedVersion: 3 }),
      }),
    );
  });

  it("同时提交优先级与期望发布时间", async () => {
    const client = { request: vi.fn().mockResolvedValue({ id: 30, version: 4 }) };

    await updateWorkItem(30, { priority: "URGENT", dueAt: "2026-10-01T00:00:00.000Z", expectedVersion: 3 }, client);

    expect(client.request).toHaveBeenCalledWith(
      "/v1/work-items/30",
      expect.objectContaining({
        method: "PATCH",
        body: JSON.stringify({ priority: "URGENT", dueAt: "2026-10-01T00:00:00.000Z", expectedVersion: 3 }),
      }),
    );
  });
});

describe("requirement delete API", () => {
  it("使用聚合版本删除需求", async () => {
    const client = { request: vi.fn().mockResolvedValue(undefined) };

    await deleteRequirement(30, 5, client);

    expect(client.request).toHaveBeenCalledWith("/v1/requirements/30?expectedVersion=5", { method: "DELETE" });
  });
});
