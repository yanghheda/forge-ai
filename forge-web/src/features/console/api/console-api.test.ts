import { describe, expect, it, vi } from "vitest";

import { listBoardRequirements } from "./console-api";

describe("task board requirement options API", () => {
  it("不传负责人时读取公司全部需求选项", async () => {
    const client = { request: vi.fn().mockResolvedValue([]) };

    await listBoardRequirements(undefined, client);

    expect(client.request).toHaveBeenCalledWith("/v1/task-board/requirements");
  });

  it("传入负责人时按负责人过滤需求选项", async () => {
    const client = { request: vi.fn().mockResolvedValue([]) };

    await listBoardRequirements(7, client);

    expect(client.request).toHaveBeenCalledWith("/v1/task-board/requirements?assigneeUserId=7");
  });
});