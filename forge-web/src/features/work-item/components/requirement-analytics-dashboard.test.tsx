import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { RequirementAnalyticsDashboard } from "./requirement-analytics-dashboard";

const api = vi.hoisted(() => ({
  getRequirementAnalytics: vi.fn(),
}));

vi.mock("../api/work-item-api", async () => ({
  ...(await vi.importActual("../api/work-item-api")),
  ...api,
}));

/* jsdom 没有 canvas，图表在测试中退化为带 aria-label 的占位元素。 */
vi.mock("./echart", () => ({
  EChart: ({ ariaLabel }: { ariaLabel: string }) => <div role="img" aria-label={ariaLabel} />,
}));

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;
}

describe("RequirementAnalyticsDashboard", () => {
  afterEach(cleanup);

  beforeEach(() => {
    api.getRequirementAnalytics.mockResolvedValue({
      rangeDays: 30,
      metrics: {
        total: 42,
        inProgress: 18,
        completed: 21,
        overdue: 4,
        createdInRange: 9,
        completedInRange: 7,
        onTimeRate: 0.86,
        avgCycleDays: 6.4,
        createdDeltaRate: 0.5,
        completedDeltaRate: -0.2,
      },
      statusFunnel: [
        { status: "DRAFT", total: 3 },
        { status: "IN_DEVELOPMENT", total: 5 },
      ],
      priorityDistribution: [
        { priority: "URGENT", total: 2 },
        { priority: "HIGH", total: 6 },
      ],
      flowTrend: [
        { date: "2026-09-01", created: 2, completed: 1 },
        { date: "2026-09-02", created: 1, completed: 3 },
      ],
      backlogTrend: [
        { date: "2026-09-01", value: 21 },
        { date: "2026-09-02", value: 19 },
      ],
      bugDistribution: [{ severity: "MAJOR", status: "OPEN", total: 3 }],
      qaQuality: { passed: 40, failed: 6, blocked: 2, skipped: 1, notRun: 0, passRate: 0.833 },
      risks: [{ id: 12, itemKey: "FORGE-12", title: "发布前联调阻塞", status: "IN_DEVELOPMENT", priority: "HIGH", dueAt: "2026-09-01T00:00:00Z", blocked: false }],
    });
  });

  it("默认按 30 天窗口拉取聚合数据并展示六项核心指标", async () => {
    render(<RequirementAnalyticsDashboard />, { wrapper });

    expect(await screen.findByText("需求总量")).toBeInTheDocument();
    expect(api.getRequirementAnalytics).toHaveBeenCalledWith(30);
    expect(screen.getByText("需求总量").closest("article")).toHaveTextContent("42");
    expect(screen.getByText("进行中").closest("article")).toHaveTextContent("18");
    expect(screen.getByText("已完成").closest("article")).toHaveTextContent("21");
    expect(screen.getByText("超期未完成").closest("article")).toHaveTextContent("4");
    expect(screen.getByText("按时交付率").closest("article")).toHaveTextContent("86%");
    expect(screen.getByText("平均交付周期").closest("article")).toHaveTextContent("6.4 天");
  });

  it("核心指标常驻滑块上方，三个分区按滑块切换渲染", async () => {
    render(<RequirementAnalyticsDashboard />, { wrapper });
    const user = userEvent.setup();

    expect(await screen.findByText("状态分布")).toBeInTheDocument();
    expect(screen.getByText("需求总量")).toBeInTheDocument();
    expect(screen.queryByText("新增 vs 完成")).not.toBeInTheDocument();

    await user.click(screen.getByText("交付趋势"));
    expect(await screen.findByText("新增 vs 完成")).toBeInTheDocument();
    expect(screen.getByText("存量燃尽")).toBeInTheDocument();

    await user.click(screen.getByText("质量与风险"));
    expect(await screen.findByText("缺陷分布")).toBeInTheDocument();
    expect(screen.getByText("QA 通过率")).toBeInTheDocument();
    expect(screen.getByText("风险预警")).toBeInTheDocument();
    expect(screen.getByText("83%")).toBeInTheDocument();
    expect(screen.getByText("FORGE-12")).toBeInTheDocument();
    expect(screen.getByText("发布前联调阻塞").closest("a")).toHaveAttribute("href", "/requirements/12");
  });

  it("切换时间范围后按新窗口重新查询", async () => {
    render(<RequirementAnalyticsDashboard />, { wrapper });
    const user = userEvent.setup();

    await screen.findByText("需求总量");
    await user.click(screen.getByText("7 天"));

    await waitFor(() => expect(api.getRequirementAnalytics).toHaveBeenLastCalledWith(7));
  });

  it("点击新建需求后在抽屉中展示创建表单", async () => {
    render(<RequirementAnalyticsDashboard />, { wrapper });
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "新建需求" }));

    expect(screen.getByLabelText("需求标题")).toBeInTheDocument();
    expect(document.querySelector(".arco-drawer-header-title")).toHaveTextContent("创建需求");
  });
});
