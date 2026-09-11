import { cleanup, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useShellStore } from "@/stores/use-shell-store";

import { AppShell } from "./app-shell";

const wrapper = ({ children }: { children: React.ReactNode }) => <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;

vi.mock("next/navigation", () => ({ usePathname: () => "/overview" }));

describe("AppShell", () => {
  beforeEach(() => {
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
    render(<AppShell><p>内容</p></AppShell>, { wrapper });

    expect(screen.getByRole("link", { name: /需求概览/ })).toHaveAttribute("href", "/overview");
    expect(screen.getByRole("link", { name: /我的需求/ })).toHaveAttribute("href", "/my-requirements");
    expect(screen.getByRole("link", { name: /任务看板/ })).toHaveAttribute("href", "/task-board");
    expect(screen.getAllByRole("link", { name: /Agent 指令/ })[0]).toHaveAttribute("href", "/agent/command");
    expect(screen.getByRole("link", { name: /执行轨迹/ })).toHaveAttribute("href", "/agent/trace/demo-run");
    expect(screen.queryByText("全部项目")).not.toBeInTheDocument();
    expect(screen.getByRole("presentation").getAttribute("src")).toMatch(/\/api\/v1\/branding\/company-logo$/);
  });
});
