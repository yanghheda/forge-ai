import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ConversationComposer } from "./conversation-composer";

const api = vi.hoisted(() => ({ listOrganizationRequirements: vi.fn() }));

vi.mock("@/features/work-item", async () => ({
  ...(await vi.importActual("@/features/work-item")),
  listOrganizationRequirements: api.listOrganizationRequirements,
}));

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;
}

const defaults = {
  activeConversation: true,
  onRequirementChange: vi.fn(),
  draft: "",
  onDraftChange: vi.fn(),
  running: false,
  sending: false,
  cancelling: false,
  onSend: vi.fn(),
  onCancel: vi.fn(),
};

describe("ConversationComposer", () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.clearAllMocks();
    api.listOrganizationRequirements.mockResolvedValue({
      items: [{ id: 20, itemKey: "REQ-20", title: "智能推荐优化" }],
      page: 1,
      pageSize: 20,
      total: 1,
    });
  });

  it("允许未绑定的新会话搜索并选择需求", async () => {
    const onRequirementChange = vi.fn();
    const user = userEvent.setup();
    render(<ConversationComposer {...defaults} onRequirementChange={onRequirementChange} />, { wrapper });

    await user.click(screen.getByRole("combobox", { name: "当前需求" }));
    fireEvent.click(await screen.findByText("REQ-20 · 智能推荐优化"));

    expect(onRequirementChange).toHaveBeenCalledWith(20);
  });

  it("展示并锁定已经持久化绑定的需求", () => {
    render(<ConversationComposer {...defaults} boundRequirement={{ id: 20, itemKey: "REQ-20", title: "智能推荐优化" }} />, { wrapper });

    expect(screen.getByText("REQ-20 · 智能推荐优化")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "当前需求" })).toHaveAttribute("aria-disabled", "true");
  });

  it("Enter 发送消息而 Shift+Enter 保留换行", async () => {
    const onSend = vi.fn();
    const user = userEvent.setup();
    render(<ConversationComposer {...defaults} draft="整理当前需求" onSend={onSend} />, { wrapper });
    const input = screen.getByPlaceholderText("描述你希望 Agent 完成的任务…");

    await user.type(input, "{Shift>}{Enter}{/Shift}");
    expect(onSend).not.toHaveBeenCalled();

    await user.type(input, "{Enter}");
    expect(onSend).toHaveBeenCalledOnce();
  });
});
