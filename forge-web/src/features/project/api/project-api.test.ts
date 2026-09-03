import { describe, expect, it, vi } from "vitest";

import { archiveProject, createProject, listProjects } from "./project-api";

describe("project API", () => {
  it("按 Workspace 范围读取项目，且不把路由 slug 当作服务端授权依据", async () => {
    const client = { request: vi.fn().mockResolvedValue([]) };

    await expect(listProjects(42, client)).resolves.toEqual([]);
    expect(client.request).toHaveBeenCalledWith("/v1/projects?workspaceId=42");
  });

  it("创建与归档均通过统一 transport 发起，归档携带 expectedVersion", async () => {
    const client = { request: vi.fn().mockResolvedValue({ id: 9 }) };

    await createProject({ workspaceId: 42, key: "FORGE", name: "ForgeAI", description: "" }, client);
    await archiveProject({ workspaceId: 42, projectId: 9, expectedVersion: 3 }, client);

    expect(client.request).toHaveBeenNthCalledWith(1, "/v1/projects", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ workspaceId: 42, key: "FORGE", name: "ForgeAI", description: "" }),
    });
    expect(client.request).toHaveBeenNthCalledWith(2, "/v1/projects/9?workspaceId=42", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ expectedVersion: 3 }),
    });
  });
});
