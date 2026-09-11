import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { RequirementDashboard } from "./requirement-dashboard";

const api = vi.hoisted(() => ({
  getRequirementOverview: vi.fn(),
  listOrganizationRequirements: vi.fn(),
  createOrganizationRequirement: vi.fn(),
  getDashboardOverview: vi.fn(),
}));

vi.mock("../api/work-item-api", async () => ({
  ...(await vi.importActual("../api/work-item-api")),
  ...api,
}));

vi.mock("@/features/console", async () => ({
  ...(await vi.importActual("@/features/console")),
  getDashboardOverview: api.getDashboardOverview,
}));

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;
}

describe("RequirementDashboard", () => {
  afterEach(cleanup);

  beforeEach(() => {
    api.getRequirementOverview.mockResolvedValue({ total: 8, inProgress: 5, completed: 3 });
    api.getDashboardOverview.mockResolvedValue({
      weeklyDeliveries: 3,
      activeAgents: 4,
      personalTodos: 3,
      stageDistribution: { IN_DEVELOPMENT: 4 },
      deliveryTrend: [],
    });
    api.listOrganizationRequirements.mockResolvedValue({
      items: [
        {
          id: 1,
          itemKey: "REQ-1",
          title: "公司级需求",
          description: "",
          status: "DRAFT",
          priority: "HIGH",
          version: 0,
          createdAt: "2026-09-09",
          updatedAt: "2026-09-09",
        },
      ],
      page: 1,
      pageSize: 20,
      total: 1,
    });
  });

  it("展示三项需求统计、创建入口和可搜索列表", async () => {
    render(<RequirementDashboard />, { wrapper });

    expect(await screen.findByText("5")).toBeInTheDocument();
    expect(screen.getByText("进行中需求")).toBeInTheDocument();
    expect(screen.getByText("待我处理")).toBeInTheDocument();
    expect(screen.getByText("本周已交付")).toBeInTheDocument();
    expect(screen.getByText("活跃 Agent")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("搜索需求标题、编号或描述")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "新建需求" })).toBeInTheDocument();
    expect(await screen.findByText("公司级需求")).toBeInTheDocument();
  });

  it("点击新建需求后在抽屉中展示创建表单", async () => {
    render(<RequirementDashboard />, { wrapper });
    const user = userEvent.setup();

    expect(screen.queryByLabelText("需求标题")).not.toBeInTheDocument();
    await user.click(await screen.findByRole("button", { name: "新建需求" }));

    expect(screen.getByLabelText("需求标题")).toBeInTheDocument();
    expect(screen.getByLabelText("需求描述")).toBeInTheDocument();
    expect(screen.getByLabelText("需求优先级")).toBeInTheDocument();
    expect(screen.getByLabelText("新建需求表单")).toHaveClass("arco-form");
    expect(document.querySelector(".arco-drawer-header-title")).toHaveTextContent("创建需求");
    expect(screen.getByText("记录问题背景与优先级，创建后可继续完善协作人员和交付材料。").closest(".arco-drawer-content")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "创建需求" })).toBeDisabled();
  });
});
