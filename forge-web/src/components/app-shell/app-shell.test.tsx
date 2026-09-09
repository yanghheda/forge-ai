import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useShellStore } from "@/stores/use-shell-store";

import { AppShell } from "./app-shell";

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
    );

    await user.click(screen.getByRole("button", { name: "收起导航" }));

    expect(useShellStore.getState().navigationCollapsed).toBe(true);
    expect(screen.getByRole("button", { name: "展开导航" })).toBeInTheDocument();
    expect(screen.getByText("页面内容")).toBeInTheDocument();
  });

  it("以需求维度展示公司级导航", () => {
    render(<AppShell><p>内容</p></AppShell>);

    expect(screen.getByRole("link", { name: /当前需求概览/ })).toHaveAttribute("href", "/overview");
    expect(screen.getByRole("link", { name: /我的需求/ })).toHaveAttribute("href", "/my-requirements");
    expect(screen.queryByText("全部项目")).not.toBeInTheDocument();
  });
});
