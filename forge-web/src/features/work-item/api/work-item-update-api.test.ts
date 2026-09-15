import { describe, expect, it, vi } from "vitest";

import { updateWorkItem } from "./work-item-api";

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
});
