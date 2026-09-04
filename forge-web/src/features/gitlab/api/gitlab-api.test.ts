import { describe, expect, it, vi } from "vitest";

import { bindRepository, createConnection, listConnections, rotateToken, testConnection } from "./gitlab-api";

describe("gitlab api", () => {
  it("按 Workspace 发送连接操作且公开响应不建模 Token", async () => {
    const request = vi.fn().mockResolvedValue({ id: 3, tokenFingerprint: "fp-safe" });
    const client = { request };

    await createConnection({ workspaceId: 7, name: "Primary", baseUrl: "https://gitlab.com", token: "secret" }, client);
    await listConnections(7, client);
    await rotateToken(7, 3, 0, "replacement", client);
    await testConnection(7, 3, client);
    await bindRepository({ workspaceId: 7, projectId: 9, connectionId: 3, remoteProjectId: "42" }, client);

    expect(request.mock.calls.map(([path]) => path)).toEqual([
      "/v1/gitlab/connections",
      "/v1/gitlab/connections?workspaceId=7",
      "/v1/gitlab/connections/3/token",
      "/v1/gitlab/connections/3/test?workspaceId=7",
      "/v1/gitlab/repositories/bind",
    ]);
    expect(JSON.stringify(await request.mock.results[0].value)).not.toContain("secret");
  });
});
