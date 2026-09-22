import { cleanup, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/features/work-item", () => ({
  getOrganizationRequirement: vi.fn().mockResolvedValue({ itemKey: "REQ-1", status: "IN_QA" }),
  statusLabel: { IN_QA: "测试中" },
  StageTasks: () => <div>阶段任务</div>,
  RequirementWorkflowControls: () => <div>阶段操作</div>,
  DevelopmentBugs: () => null,
  QaPanel: ({ requirementId }: { requirementId: number }) => <div>真实 QA 数据面板 · {requirementId}</div>,
}));

import { DeliveryScreen } from "./delivery-screen";

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter initialEntries={["/requirements/1/qa"]}>{children}</MemoryRouter>
  </QueryClientProvider>
);

describe("DeliveryScreen", () => {
  afterEach(cleanup);

  it("QA 页面按路由需求加载真实数据面板", () => {
    render(<DeliveryScreen kind="qa" requirementId="31" />, { wrapper });

    expect(screen.getByText("真实 QA 数据面板 · 31")).toBeInTheDocument();
    expect(screen.queryByText("48")).not.toBeInTheDocument();
    expect(screen.queryByText("TC-101 正确验证码登录 · 通过")).not.toBeInTheDocument();
  });
});
