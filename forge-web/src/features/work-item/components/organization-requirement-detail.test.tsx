import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { OrganizationRequirementDetail } from "./organization-requirement-detail";

const api = vi.hoisted(() => ({
  getOrganizationRequirement: vi.fn(),
  getRequirementParticipants: vi.fn(),
  listRequirementMembers: vi.fn(),
  getRequirementWorkflow: vi.fn(),
  getRequirementDetails: vi.fn(),
  getRequirementActivity: vi.fn(),
}));

vi.mock("../api/work-item-api", async () => ({
  ...(await vi.importActual("../api/work-item-api")),
  ...api,
}));

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;
}

describe("OrganizationRequirementDetail", () => {
  afterEach(cleanup);

  beforeEach(() => {
    api.getOrganizationRequirement.mockResolvedValue({
      id: 1,
      itemKey: "DEMO-1",
      title: "黄金 Demo：AI 需求交付全流程演示",
      description: "完整交付链路说明",
      status: "IN_DEVELOPMENT",
      priority: "HIGH",
      version: 3,
      organizationName: "黄金 Demo 项目",
      reporterName: "张产品",
      dueAt: "2026-09-20T00:00:00Z",
      createdAt: "2026-09-01T10:24:00Z",
      updatedAt: "2026-09-10T09:41:00Z",
    });
    api.getRequirementParticipants.mockResolvedValue([{ role: "DEVELOPER", userId: 2, displayName: "王开发", email: "dev@example.com" }]);
    api.listRequirementMembers.mockResolvedValue([]);
    api.getRequirementWorkflow.mockResolvedValue({ version: 3, availableActions: ["SUBMIT_FOR_QA"], guardHints: { SUBMIT_FOR_QA: ["completedDevTasks"] } });
    api.getRequirementDetails.mockResolvedValue({ goal: "可重复演示", inScope: "全流程", outOfScope: "真实部署", acceptanceCriteria: ["固定状态机推进"], businessValue: "售前演示", version: 1 });
    api.getRequirementActivity.mockResolvedValue([]);
  });

  it("按照设计稿展示标题、六阶段、基本信息、描述和右侧阶段信息", async () => {
    render(<OrganizationRequirementDetail requirementId={1} />, { wrapper });

    expect(await screen.findByText("黄金 Demo：AI 需求交付全流程演示")).toBeInTheDocument();
    expect(screen.getAllByText("产品评审").length).toBeGreaterThan(0);
    expect(screen.getByText("UX 设计")).toBeInTheDocument();
    expect(screen.getByText("UX 评审")).toBeInTheDocument();
    expect(screen.getByText("QA 测试")).toBeInTheDocument();
    expect(screen.getByText("基本信息")).toBeInTheDocument();
    expect(screen.getByText("需求描述")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "编辑需求" })).toBeInTheDocument();
    expect(screen.getByText("阶段 · 开发中")).toBeInTheDocument();
    expect(screen.getByText("协作成员")).toBeInTheDocument();
    expect(screen.getByText("张产品")).toBeInTheDocument();
    expect(screen.getByText("王开发")).toBeInTheDocument();
  });
});
