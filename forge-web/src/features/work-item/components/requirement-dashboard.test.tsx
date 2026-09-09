import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { RequirementDashboard } from "./requirement-dashboard";

const api = vi.hoisted(() => ({
  getRequirementOverview: vi.fn(),
  listOrganizationRequirements: vi.fn(),
  createOrganizationRequirement: vi.fn(),
}));

vi.mock("../api/work-item-api", async () => ({
  ...await vi.importActual("../api/work-item-api"),
  ...api,
}));

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;
}

describe("RequirementDashboard", () => {
  beforeEach(() => {
    api.getRequirementOverview.mockResolvedValue({ total: 8, inProgress: 5, completed: 3 });
    api.listOrganizationRequirements.mockResolvedValue({
      items: [{ id: 1, itemKey: "REQ-1", title: "公司级需求", description: "", status: "DRAFT", priority: "HIGH", version: 0, createdAt: "2026-09-09", updatedAt: "2026-09-09" }],
      page: 1, pageSize: 20, total: 1,
    });
  });

  it("展示三项需求统计、创建入口和可搜索列表", async () => {
    render(<RequirementDashboard />, { wrapper });

    expect(await screen.findByText("8")).toBeInTheDocument();
    expect(screen.getByText("全部需求")).toBeInTheDocument();
    expect(screen.getByText("进行中")).toBeInTheDocument();
    expect(screen.getByText("已完成")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("搜索需求标题、编号或描述")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "新建需求" })).toBeInTheDocument();
    expect(await screen.findByText("公司级需求")).toBeInTheDocument();
  });
});
