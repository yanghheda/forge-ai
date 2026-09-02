import { describe, expect, it, vi } from "vitest";

import { loadProjectOverview } from "./project-overview-api";

describe("loadProjectOverview", () => {
  it("将 transport 的未知响应收敛为页面视图数据", async () => {
    const client = {
      request: vi.fn().mockResolvedValue({ application: "forge-server", status: "UP" }),
    };

    await expect(loadProjectOverview(client)).resolves.toEqual({
      serviceName: "forge-server",
      serviceStatus: "UP",
    });
  });

  it("响应缺少必要字段时返回空态而不是伪造项目数据", async () => {
    const client = { request: vi.fn().mockResolvedValue({ status: "UP" }) };

    await expect(loadProjectOverview(client)).resolves.toBeNull();
  });
});
