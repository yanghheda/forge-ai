import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Message } from "@arco-design/web-react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { OrganizationRequirementDetail } from "./organization-requirement-detail";

const api = vi.hoisted(() => ({
  getOrganizationRequirement: vi.fn(),
  getRequirementParticipants: vi.fn(),
  listRequirementMembers: vi.fn(),
  getRequirementWorkflow: vi.fn(),
  getRequirementDetails: vi.fn(),
  getRequirementActivity: vi.fn(),
  transitionRequirementWorkflow: vi.fn(),
  updateWorkItem: vi.fn(),
}));

vi.mock("../api/work-item-api", async () => ({
  ...(await vi.importActual("../api/work-item-api")),
  ...api,
}));

function wrapper({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={["/requirements/1"]}>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("OrganizationRequirementDetail", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  beforeEach(() => {
    vi.spyOn(Message, "success").mockImplementation(() => () => undefined);
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

  it("产品评审中展示通过与退回入口并携带退回原因", async () => {
    api.getOrganizationRequirement.mockResolvedValue({
      ...(await api.getOrganizationRequirement()),
      status: "PRODUCT_REVIEW",
    });
    api.getRequirementWorkflow.mockResolvedValue({
      version: 4,
      availableActions: ["APPROVE_PRODUCT_REVIEW", "REJECT_PRODUCT_REVIEW"],
      guardHints: {},
    });
    api.transitionRequirementWorkflow.mockResolvedValue({});

    render(<OrganizationRequirementDetail requirementId={1} />, { wrapper });

    expect(await screen.findByText("需求阶段操作")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "确认评审通过" })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("退回原因"), { target: { value: "补充异常场景" } });
    fireEvent.click(screen.getByRole("button", { name: "退回产品评审" }));

    await waitFor(() => expect(api.transitionRequirementWorkflow).toHaveBeenCalledWith(1, "REJECT_PRODUCT_REVIEW", 4, { reason: "补充异常场景", checklist: [] }));
  });

  it("按照设计稿展示标题、六阶段、基本信息、描述、动态时间线与右侧协作成员", async () => {
    render(<OrganizationRequirementDetail requirementId={1} />, { wrapper });

    expect(await screen.findByText("黄金 Demo：AI 需求交付全流程演示")).toBeInTheDocument();
    expect(screen.getAllByText("产品评审").length).toBeGreaterThan(0);
    expect(screen.getByText("UX 设计")).toBeInTheDocument();
    expect(screen.queryByText("UX 评审")).not.toBeInTheDocument();
    expect(screen.getByRole("list", { name: "需求交付阶段" }).querySelectorAll("li")).toHaveLength(6);
    expect(screen.getByText("QA 测试")).toBeInTheDocument();
    expect(screen.getByText("基本信息")).toBeInTheDocument();
    expect(screen.getByText("需求描述")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "编辑描述" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "编辑需求" })).toBeInTheDocument();
    expect(screen.getByText("动态时间线")).toBeInTheDocument();
    expect(screen.getByText("协作成员")).toBeInTheDocument();
    expect(screen.getByText("张产品")).toBeInTheDocument();
    expect(screen.getAllByText("王开发").length).toBeGreaterThan(0);
  });

  it("需求阶段操作展示在动态时间线之前", async () => {
    render(<OrganizationRequirementDetail requirementId={1} />, { wrapper });

    await screen.findByText("需求阶段操作");
    const workflow = screen.getByText("需求阶段操作");
    const timeline = screen.getByText("动态时间线");
    expect(workflow.compareDocumentPosition(timeline) & Node.DOCUMENT_POSITION_FOLLOWING).not.toBe(0);
  });

  it("动态时间线限制高度并在超出时内部滚动", async () => {
    /* 动态条数会随流程推进持续增长，时间线必须封顶高度，不能撑长右侧栏。 */
    render(<OrganizationRequirementDetail requirementId={1} />, { wrapper });

    await screen.findByText("动态时间线");
    const card = screen.getByText("动态时间线").closest(".arco-card");
    expect(card).not.toBeNull();
    const timeline = card!.querySelector(".arco-card-body > div") as HTMLElement;
    expect(timeline).not.toBeNull();
    expect(getComputedStyle(timeline).overflowY).toBe("auto");
    expect(getComputedStyle(timeline).maxHeight).not.toBe("none");
  });

  it("允许人工编辑需求描述并携带最新聚合版本", async () => {
    api.updateWorkItem.mockResolvedValue({ id: 1, description: "人工补充后的描述", version: 4 });
    render(<OrganizationRequirementDetail requirementId={1} />, { wrapper });

    await screen.findByText("完整交付链路说明");
    fireEvent.click(screen.getByRole("button", { name: "编辑描述" }));
    fireEvent.change(screen.getByLabelText("需求描述正文"), {
      target: { value: "人工补充后的描述" },
    });
    fireEvent.click(screen.getByRole("button", { name: "保存描述" }));

    await waitFor(() =>
      expect(api.updateWorkItem).toHaveBeenCalledWith(1, {
        description: "人工补充后的描述",
        expectedVersion: 3,
      }),
    );
  });

  it("动态时间线把需求、Bug 与发布流程的动作代码渲染为中文文案", async () => {
    /* 三类流程共用活动事件列，原始动作代码不得直接暴露给用户。 */
    api.getRequirementActivity.mockResolvedValue([
      { kind: "EVENT", id: 1, actorId: 2, action: "START_UX", reason: null, body: null, createdAt: "2026-09-10T09:41:00Z" },
      { kind: "EVENT", id: 2, actorId: 2, action: "MARK_RELEASED", reason: "READY_FOR_RELEASE", body: null, createdAt: "2026-09-11T09:41:00Z" },
      { kind: "EVENT", id: 3, actorId: 2, action: "RESOLVE", reason: null, body: null, createdAt: "2026-09-12T09:41:00Z" },
      { kind: "COMMENT", id: 4, actorId: 2, action: null, reason: null, body: "补充了异常场景", createdAt: "2026-09-13T09:41:00Z" },
    ]);

    render(<OrganizationRequirementDetail requirementId={1} />, { wrapper });

    expect(await screen.findByText("开始 UX 设计")).toBeInTheDocument();
    expect(screen.getByText("标记已发布")).toBeInTheDocument();
    expect(screen.getByText("提交修复")).toBeInTheDocument();
    expect(screen.getByText("新增评论")).toBeInTheDocument();
    expect(screen.queryByText("START_UX")).not.toBeInTheDocument();
    expect(screen.queryByText("MARK_RELEASED")).not.toBeInTheDocument();
    expect(screen.queryByText("RESOLVE")).not.toBeInTheDocument();
  });

  it("需求完成后将发布阶段标记为已完成", async () => {
    api.getOrganizationRequirement.mockResolvedValue({
      id: 1,
      itemKey: "DEMO-1",
      title: "黄金 Demo：AI 需求交付全流程演示",
      description: "完整交付链路说明",
      status: "DONE",
      priority: "HIGH",
      version: 5,
      organizationName: "黄金 Demo 项目",
      reporterName: "张产品",
      dueAt: null,
      createdAt: "2026-09-01T10:24:00Z",
      updatedAt: "2026-09-12T02:07:00Z",
    });
    api.getRequirementWorkflow.mockResolvedValue({ version: 5, availableActions: [], guardHints: {} });

    render(<OrganizationRequirementDetail requirementId={1} />, { wrapper });

    expect(await screen.findByText("黄金 Demo：AI 需求交付全流程演示")).toBeInTheDocument();
    const stages = screen.getByRole("list", { name: "需求交付阶段" });
    expect(stages.querySelectorAll("li")[5]).toHaveTextContent("发布已完成");
    expect(screen.queryByText("已完成 · 进行中")).not.toBeInTheDocument();
  });
});
