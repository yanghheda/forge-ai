import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
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

vi.mock("react-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router")>();
  return { ...actual, useLocation: () => ({ pathname }) };
});
vi.mock("../api/console-api", () => consoleApi);
vi.mock("@/features/work-item", () => workItemApi);
vi.mock("@/features/auth", () => authApi);

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter initialEntries={[pathname]}>{children}</MemoryRouter>
  </QueryClientProvider>
);

describe("AgentCommandDrawer", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    pathname = "/overview";
    consoleApi.listAgentConversations.mockResolvedValue([{ id: 7, title: "登录态排查" }]);
    consoleApi.listAgentMessages.mockResolvedValue([]);
    authApi.getCurrentUser.mockResolvedValue({ id: 1, organization: { id: 2 } });
    workItemApi.getOrganizationRequirement.mockResolvedValue({ id: 31, itemKey: "REQ-31", title: "找回密码" });
    useAgentPanelStore.setState({ open: false, activeConversationId: undefined, running: false, awaitingApproval: false, contextDismissed: false, context: undefined });
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

  it("列表页显式交给 Agent 的需求在抽屉挂载后仍然生效", async () => {
    pathname = "/requirements";
    useAgentPanelStore.setState({ open: true, context: { kind: "requirement", id: 31, label: "REQ-31 找回密码" } });

    render(<AgentCommandDrawer />, { wrapper });

    expect(await screen.findByText(/当前需求 · REQ-31 找回密码/)).toBeInTheDocument();
    await waitFor(() => expect(workItemApi.getOrganizationRequirement).toHaveBeenCalledWith(31));
  });

  it("缺陷列表页交给 Agent 时展示缺陷上下文且不查询需求", async () => {
    pathname = "/defects";
    useAgentPanelStore.setState({ open: true, context: { kind: "defect", id: 5, label: "BUG-5 登录报错" } });

    render(<AgentCommandDrawer />, { wrapper });

    expect(await screen.findByText(/当前缺陷 · BUG-5 登录报错/)).toBeInTheDocument();
    expect(workItemApi.getOrganizationRequirement).not.toHaveBeenCalled();
  });
});
