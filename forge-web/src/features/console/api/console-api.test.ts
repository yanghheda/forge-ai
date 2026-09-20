import { describe, expect, it, vi } from "vitest";

import { createAgentConversation, deleteAgentConversation } from "./console-api";

describe("conversation api", () => {
  it("创建空白会话并由服务端负责默认标题", async () => {
    const client = { request: vi.fn().mockResolvedValue({ id: 7, title: "新会话" }) };

    await createAgentConversation(client);

    expect(client.request).toHaveBeenCalledWith("/v1/agent-conversations", expect.objectContaining({ method: "POST", body: "{}" }));
  });

  it("删除指定会话", async () => {
    const client = { request: vi.fn().mockResolvedValue(undefined) };

    await deleteAgentConversation(7, client);

    expect(client.request).toHaveBeenCalledWith("/v1/agent-conversations/7", { method: "DELETE" });
  });
});
