import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useAgentPanelStore } from "@/stores/use-agent-panel-store";
import { useShellStore } from "@/stores/use-shell-store";

import { AppShell } from "./app-shell";

const replace = vi.fn();
const authApi = vi.hoisted(() => ({
  getCurrentUser: vi.fn(),
  logout: vi.fn(),
}));
const consoleApi = vi.hoisted(() => ({
  listAgentConversations: vi.fn(),
  listAgentMessages: vi.fn(),
  createAgentConversation: vi.fn(),
  sendAgentMessage: vi.fn(),
  getUnreadNotificationCount: vi.fn(),
  listNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  searchConsole: vi.fn(),
}));

let testQueryClient: QueryClient;
const wrapper = ({ children }: { children: React.ReactNode }) => (
  <QueryClientProvider client={testQueryClient}>
    <MemoryRouter initialEntries={["/overview"]}>{children}</MemoryRouter>
  </QueryClientProvider>
);

// 仅覆盖需要替换为对象返回值的 react-router Hook，保留真实 Link 等其他导出；
// Link 仍需路由上下文，因此渲染时由上面的 wrapper 提供 MemoryRouter。
vi.mock("react-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router")>();
  return {
    ...actual,
    useLocation: () => ({ pathname: "/overview" }),
    useNavigate: () => replace,
  };
});
vi.mock("@/features/auth", () => authApi);
vi.mock("@/features/console/api/console-api", () => consoleApi);

describe("AppShell", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authApi.getCurrentUser.mockResolvedValue({
      id: 1,
      email: "yang@example.com",
      displayName: "杨佳锡",
      organization: { id: 2, slug: "forge", name: "Forge", roles: ["OWNER"] },
    });
    authApi.logout.mockResolvedValue(undefined);
    consoleApi.getUnreadNotificationCount.mockResolvedValue({ count: 0 });
    consoleApi.listAgentConversations.mockResolvedValue([]);
    consoleApi.listAgentMessages.mockResolvedValue([]);
    testQueryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    testQueryClient.setQueryData(["current-user"], {
      id: 1,
      email: "yang@example.com",
      displayName: "杨佳锡",
      organization: { id: 2, slug: "forge", name: "Forge", roles: ["OWNER"] },
    });
    useShellStore.setState({ navigationCollapsed: false });
    useAgentPanelStore.setState({ open: false, activeConversationId: undefined, running: false, awaitingApproval: false, contextDismissed: false });
  });
  afterEach(cleanup);

  it("只通过 UI store 切换导航显示状态", async () => {
    const user = userEvent.setup();
    render(
      <AppShell>
        <p>页面内容</p>
      </AppShell>,
      { wrapper },
    );

    await user.click(screen.getByRole("button", { name: "收起导航" }));

    expect(useShellStore.getState().navigationCollapsed).toBe(true);
    expect(screen.getByRole("button", { name: "展开导航" })).toBeInTheDocument();
    expect(screen.getByText("页面内容")).toBeInTheDocument();
  });

  it("以需求维度展示公司级导航", () => {
    render(
      <AppShell>
        <p>内容</p>
      </AppShell>,
      { wrapper },
    );

    expect(screen.getByRole("link", { name: /全局仪表盘/ })).toHaveAttribute("href", "/overview");
    expect(screen.getByRole("link", { name: /需求列表/ })).toHaveAttribute("href", "/requirements");
    expect(screen.getByRole("link", { name: /缺陷列表/ })).toHaveAttribute("href", "/defects");
    expect(screen.queryByRole("link", { name: /我的需求/ })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /任务看板/ })).toHaveAttribute("href", "/task-board");
    expect(screen.getAllByRole("link", { name: /Agent 指令/ })[0]).toHaveAttribute("href", "/agent/command");
    expect(screen.getByRole("link", { name: /执行轨迹/ })).toHaveAttribute("href", "/agent/trace/demo-run");
    expect(screen.queryByText("全部项目")).not.toBeInTheDocument();
  });

  it("顶栏 Agent 指令按钮在任意页面唤起全局抽屉而非跳转", async () => {
    const user = userEvent.setup();
    render(
      <AppShell>
        <p>内容</p>
      </AppShell>,
      { wrapper },
    );

    await user.click(screen.getByRole("button", { name: /Agent 指令/ }));

    expect(useAgentPanelStore.getState().open).toBe(true);
    expect(await screen.findByPlaceholderText("描述你希望 Agent 完成的任务…")).toBeInTheDocument();
  });

  it("⌘J 唤起全局 Agent 抽屉", async () => {
    render(
      <AppShell>
        <p>内容</p>
      </AppShell>,
      { wrapper },
    );

    fireEvent.keyDown(window, { key: "j", metaKey: true });

    expect(useAgentPanelStore.getState().open).toBe(true);
  });

  it("头像使用当前用户姓名首字且点击后从菜单退出", async () => {
    const user = userEvent.setup();
    render(
      <AppShell>
        <p>内容</p>
      </AppShell>,
      { wrapper },
    );

    const accountMenu = await screen.findByRole("button", { name: "账户菜单" });
    await waitFor(() => expect(accountMenu).toHaveTextContent("杨"));
    expect(screen.queryByText("杨佳锡")).not.toBeInTheDocument();

    await user.click(accountMenu);
    fireEvent.click(await screen.findByText("退出登录"));

    await waitFor(() => expect(authApi.logout).toHaveBeenCalledOnce());
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login", { replace: true }));
  });
});
