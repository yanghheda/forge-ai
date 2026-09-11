import { describe, expect, it, vi } from "vitest";

import { listPipelines, triggerPipeline } from "./gitlab-api";

describe("pipeline API", () => {
  it("在当前公司读取快照并触发指定 ref", async () => {
    const client = { request: vi.fn().mockResolvedValue([]) };

    await listPipelines(client);
    await triggerPipeline({ ref: "main" }, client);

    expect(client.request).toHaveBeenNthCalledWith(1, "/v1/development/pipelines");
    expect(client.request).toHaveBeenNthCalledWith(2, "/v1/development/pipelines", expect.objectContaining({ method: "POST" }));
  });
});
