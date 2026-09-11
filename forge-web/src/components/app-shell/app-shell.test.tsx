import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useShellStore } from "@/stores/use-shell-store";

import { AppShell } from "./app-shell";

const replace = vi.fn();
const authApi = vi.hoisted(() => ({
  getCurrentUser: vi.fn(),
  logout: vi.fn(),
}));

let testQueryClient: QueryClient;
const wrapper = ({ children }: { children: React.ReactNode }) => <QueryClientProvider client={testQueryClient}>{children}</QueryClientProvider>;

vi.mock("next/navigation", () => ({
  usePathname: () => "/overview",
  useRouter: () => ({ replace }),
}));
vi.mock("@/features/auth", () => authApi);

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
    testQueryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    testQueryClient.setQueryData(["current-user"], {
      id: 1,
      email: "yang@example.com",
      displayName: "杨佳锡",
      organization: { id: 2, slug: "forge", name: "Forge", roles: ["OWNER"] },
    });
    useShellStore.setState({ navigationCollapsed: false });
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

    expect(screen.getByRole("link", { name: /需求概览/ })).toHaveAttribute("href", "/overview");
    expect(screen.getByRole("link", { name: /我的需求/ })).toHaveAttribute("href", "/my-requirements");
    expect(screen.getByRole("link", { name: /任务看板/ })).toHaveAttribute("href", "/task-board");
    expect(screen.getAllByRole("link", { name: /Agent 指令/ })[0]).toHaveAttribute("href", "/agent/command");
    expect(screen.getByRole("link", { name: /执行轨迹/ })).toHaveAttribute("href", "/agent/trace/demo-run");
    expect(screen.queryByText("全部项目")).not.toBeInTheDocument();
    expect(screen.getByRole("presentation").getAttribute("src")).toMatch(/\/api\/v1\/branding\/company-logo$/);
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
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
  });
});
