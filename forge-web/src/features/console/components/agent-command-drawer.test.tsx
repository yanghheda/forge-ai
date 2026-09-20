import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useAgentPanelStore } from "@/stores/use-agent-panel-store";

import { AgentCommandDrawer } from "./agent-command-drawer";

const consoleApi = vi.hoisted(() => ({
  listAgentConversations: vi.fn(),
  listAgentMessages: vi.fn(),
  createAgentConversation: vi.fn(),
  sendAgentMessage: vi.fn(),
}));
const workItemApi = vi.hoisted(() => ({ getOrganizationRequirement: vi.fn() }));
const authApi = vi.hoisted(() => ({ getCurrentUser: vi.fn() }));
let pathname = "/overview";

vi.mock("next/navigation", () => ({ usePathname: () => pathname }));
vi.mock("../api/console-api", () => consoleApi);
vi.mock("@/features/work-item", () => workItemApi);
vi.mock("@/features/auth", () => authApi);

const wrapper = ({ children }: { children: React.ReactNode }) => <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;

describe("AgentCommandDrawer", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    pathname = "/overview";
    consoleApi.listAgentConversations.mockResolvedValue([{ id: 7, title: "登录态排查" }]);
    consoleApi.listAgentMessages.mockResolvedValue([]);
    authApi.getCurrentUser.mockResolvedValue({ id: 1, organization: { id: 2 } });
    workItemApi.getOrganizationRequirement.mockResolvedValue({ id: 31, itemKey: "REQ-31", title: "找回密码" });
    useAgentPanelStore.setState({ open: false, activeConversationId: undefined, running: false, awaitingApproval: false, contextDismissed: false });
  });
  afterEach(cleanup);

  it("抽屉关闭时不加载会话，展开后才拉取并默认选中首个会话", async () => {
    render(<AgentCommandDrawer />, { wrapper });

    expect(consoleApi.listAgentConversations).not.toHaveBeenCalled();

    useAgentPanelStore.setState({ open: true });

    await waitFor(() => expect(consoleApi.listAgentConversations).toHaveBeenCalledOnce());
    await waitFor(() => expect(useAgentPanelStore.getState().activeConversationId).toBe(7));
    expect(await screen.findByPlaceholderText("描述你希望 Agent 完成的任务…")).toBeInTheDocument();
  });

  it("需求详情页自动绑定当前需求，可手动解绑", async () => {
    pathname = "/requirements/31/qa";
    useAgentPanelStore.setState({ open: true });

    render(<AgentCommandDrawer />, { wrapper });

    expect(await screen.findByText(/REQ-31 找回密码/)).toBeInTheDocument();
    await waitFor(() => expect(workItemApi.getOrganizationRequirement).toHaveBeenCalledWith(31));

    await userEvent.click(screen.getByRole("button", { name: "Close" }));

    expect(useAgentPanelStore.getState().contextDismissed).toBe(true);
    await waitFor(() => expect(screen.queryByText(/REQ-31 找回密码/)).not.toBeInTheDocument());
  });

  it("非需求页不请求需求上下文", async () => {
    useAgentPanelStore.setState({ open: true });

    render(<AgentCommandDrawer />, { wrapper });

    await waitFor(() => expect(consoleApi.listAgentConversations).toHaveBeenCalledOnce());
    expect(workItemApi.getOrganizationRequirement).not.toHaveBeenCalled();
    expect(screen.queryByText(/REQ-/)).not.toBeInTheDocument();
  });
});
