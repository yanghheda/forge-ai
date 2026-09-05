import { describe, expect, it, vi } from "vitest";

import { listPipelines, triggerPipeline } from "./gitlab-api";

describe("pipeline API", () => {
  it("按租户项目读取快照并触发指定 ref", async () => {
    const client = { request: vi.fn().mockResolvedValue([]) };

    await listPipelines(10, 20, client);
    await triggerPipeline({ workspaceId: 10, projectId: 20, ref: "main" }, client);

    expect(client.request).toHaveBeenNthCalledWith(
      1,
      "/v1/development/pipelines?workspaceId=10&projectId=20",
    );
    expect(client.request).toHaveBeenNthCalledWith(
      2,
      "/v1/development/pipelines",
      expect.objectContaining({ method: "POST" }),
    );
  });
});
