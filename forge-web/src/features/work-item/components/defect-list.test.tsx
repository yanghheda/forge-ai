import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api";
import { useAgentPanelStore } from "@/stores/use-agent-panel-store";
import type { BugView } from "../api/work-item-api";

const api = vi.hoisted(() => ({
  getCurrentUser: vi.fn(),
  listCompanyBugs: vi.fn(),
  listRequirementMembers: vi.fn(),
}));

vi.mock("@/features/auth", () => ({ getCurrentUser: api.getCurrentUser }));
vi.mock("../api/work-item-api", () => ({ listCompanyBugs: api.listCompanyBugs, listRequirementMembers: api.listRequirementMembers }));

import { DefectList } from "./defect-list";

const bug = (overrides: Partial<BugView>): BugView => ({
  id: 1,
  itemKey: "BUG-1",
  title: "缺陷",
  status: "TODO",
  severity: "MINOR",
  requirementId: 1,
  testRunId: null,
  testResultId: null,
  devTaskId: null,
  devTaskKey: null,
  assigneeUserId: null,
  assigneeName: null,
  reporterUserId: 1,
  reporterName: null,
  reproductionSteps: [],
  expectedResult: "",
  actualResult: "",
  fixNote: null,
  fixEvidence: [],
  version: 0,
  createdAt: "2026-09-01T00:00:00Z",
  updatedAt: "2026-09-02T00:00:00Z",
  ...overrides,
});

/* 五种状态各一条：便于校验统计窄条、状态筛选与中文状态文案。 */
const bugs: BugView[] = [
  bug({ id: 1, itemKey: "BUG-1", title: "登录接口报错", status: "TODO", severity: "BLOCKER", assigneeUserId: 2, assigneeName: "王开发", reporterName: "赵测试" }),
  bug({ id: 2, itemKey: "BUG-2", title: "验证码不刷新", status: "IN_PROGRESS", severity: "MAJOR", assigneeUserId: 9, assigneeName: "赵测试", reporterName: "王开发" }),
  bug({ id: 3, itemKey: "BUG-3", title: "支付超时", status: "BLOCKED", severity: "CRITICAL", assigneeUserId: 2, assigneeName: "王开发", reporterName: "赵测试" }),
  bug({ id: 4, itemKey: "BUG-4", title: "样式错位", status: "WAITING_VERIFICATION", severity: "MINOR", assigneeUserId: 9, assigneeName: "赵测试", reporterName: "王开发" }),
  bug({ id: 5, itemKey: "BUG-5", title: "文案错误", status: "DONE", severity: "MINOR", assigneeUserId: 2, assigneeName: "王开发", reporterName: "赵测试" }),
];

const members = [
  { userId: 2, displayName: "王开发", email: "wang@forge.ai", roles: ["DEVELOPER"] },
  { userId: 9, displayName: "赵测试", email: "zhao@forge.ai", roles: ["QA"] },
];

function wrapper({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={["/defects"]}>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

/* Arco 下拉弹层在 jsdom 中停留在进入动画态（pointer-events: none），统一用 fireEvent 选择选项。 */
async function selectOption(comboboxName: string, optionName: string) {
  const user = userEvent.setup();
  await user.click(screen.getByRole("combobox", { name: comboboxName }));
  fireEvent.click(await screen.findByRole("option", { name: optionName }));
}

describe("DefectList", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    api.getCurrentUser.mockResolvedValue({ id: 2, email: "wang@forge.ai", displayName: "王开发", organization: { id: 1, slug: "demo", name: "Demo", roles: ["DEVELOPER"] } });
    api.listRequirementMembers.mockResolvedValue(members);
    api.listCompanyBugs.mockResolvedValue(bugs);
  });

  it("默认三个筛选均为全部并请求公司缺陷列表", async () => {
    render(<DefectList />, { wrapper });

    expect(await screen.findByText("登录接口报错")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "缺陷列表" })).toBeInTheDocument();
    expect(api.listCompanyBugs).toHaveBeenCalled();
    expect(screen.getByRole("radio", { name: "全部" })).toBeChecked();
    expect(screen.getByRole("radio", { name: "我的" })).not.toBeChecked();
    expect(screen.getByRole("combobox", { name: "状态" })).toHaveTextContent("全部状态");
    expect(screen.getByRole("combobox", { name: "修复人" })).toHaveTextContent("全部");
  });

  it("选择我的后仅展示修复人为当前用户的缺陷", async () => {
    render(<DefectList />, { wrapper });
    await screen.findByText("登录接口报错");

    fireEvent.click(screen.getByRole("radio", { name: "我的" }));

    expect(screen.getByText("登录接口报错")).toBeInTheDocument();
    expect(screen.getByText("支付超时")).toBeInTheDocument();
    expect(screen.getByText("文案错误")).toBeInTheDocument();
    expect(screen.queryByText("验证码不刷新")).not.toBeInTheDocument();
    expect(screen.queryByText("样式错位")).not.toBeInTheDocument();
    expect(screen.getByTestId("defect-stat-TODO")).toHaveTextContent("1");
    expect(screen.getByTestId("defect-stat-IN_PROGRESS")).toHaveTextContent("0");
    expect(screen.getByTestId("defect-stat-BLOCKED")).toHaveTextContent("1");
    expect(screen.getByTestId("defect-stat-WAITING_VERIFICATION")).toHaveTextContent("0");
    expect(screen.getByTestId("defect-stat-DONE")).toHaveTextContent("1");
  });

  it("选择状态后仅展示该状态缺陷", async () => {
    render(<DefectList />, { wrapper });
    await screen.findByText("登录接口报错");

    await selectOption("状态", "阻塞中");

    expect(screen.getByText("支付超时")).toBeInTheDocument();
    expect(screen.queryByText("登录接口报错")).not.toBeInTheDocument();
    expect(screen.queryByText("验证码不刷新")).not.toBeInTheDocument();
    expect(screen.queryByText("样式错位")).not.toBeInTheDocument();
    expect(screen.queryByText("文案错误")).not.toBeInTheDocument();
  });

  it("按姓名选择修复人后过滤生效", async () => {
    render(<DefectList />, { wrapper });
    await screen.findByText("登录接口报错");

    await selectOption("修复人", "赵测试");

    expect(screen.getByText("验证码不刷新")).toBeInTheDocument();
    expect(screen.getByText("样式错位")).toBeInTheDocument();
    expect(screen.queryByText("登录接口报错")).not.toBeInTheDocument();
    expect(screen.queryByText("支付超时")).not.toBeInTheDocument();
    expect(screen.queryByText("文案错误")).not.toBeInTheDocument();
  });

  it("组合筛选后统计窄条、共 N 条缺陷与列表行数一致", async () => {
    render(<DefectList />, { wrapper });
    await screen.findByText("登录接口报错");

    await selectOption("状态", "阻塞中");
    await selectOption("修复人", "王开发");

    expect(screen.getByText("支付超时")).toBeInTheDocument();
    expect(screen.queryByText("登录接口报错")).not.toBeInTheDocument();
    expect(screen.getByTestId("defect-stat-BLOCKED")).toHaveTextContent("1");
    expect(screen.getByTestId("defect-stat-TODO")).toHaveTextContent("0");
    expect(screen.getAllByTestId(/^defect-row-/)).toHaveLength(1);
    expect(screen.getByText(/共 1 条缺陷/)).toBeInTheDocument();
  });

  it("状态标签使用中文文案且不出现原始状态码", async () => {
    render(<DefectList />, { wrapper });
    await screen.findByText("登录接口报错");

    expect(screen.getAllByText("待修复").length).toBeGreaterThan(0);
    expect(screen.getAllByText("修复中").length).toBeGreaterThan(0);
    expect(screen.getAllByText("阻塞中").length).toBeGreaterThan(0);
    expect(screen.getAllByText("待验证").length).toBeGreaterThan(0);
    expect(screen.getAllByText("已完成").length).toBeGreaterThan(0);
    expect(screen.queryByText("TODO")).not.toBeInTheDocument();
    expect(screen.queryByText("IN_PROGRESS")).not.toBeInTheDocument();
    expect(screen.queryByText("BLOCKED")).not.toBeInTheDocument();
    expect(screen.queryByText("WAITING_VERIFICATION")).not.toBeInTheDocument();
    expect(screen.queryByText("DONE")).not.toBeInTheDocument();
  });

  it("行标题链接指向缺陷详情", async () => {
    render(<DefectList />, { wrapper });
    await screen.findByText("登录接口报错");

    expect(screen.getByTestId("defect-row-1")).toHaveAttribute("href", "/defects/1");
    expect(screen.getByTestId("defect-row-3")).toHaveAttribute("href", "/defects/3");
  });

  it("行内「交给 Agent」写入缺陷上下文并展开指令抽屉", async () => {
    const user = userEvent.setup();
    useAgentPanelStore.setState({ open: false, context: undefined, contextDismissed: false });
    render(<DefectList />, { wrapper });
    await screen.findByText("登录接口报错");

    await user.click(screen.getAllByRole("button", { name: "交给 Agent" })[0]);

    expect(useAgentPanelStore.getState().context).toEqual({ kind: "defect", id: 1, label: "BUG-1 登录接口报错" });
    expect(useAgentPanelStore.getState().open).toBe(true);
  });

  it("接口失败时展示错误提示", async () => {
    api.listCompanyBugs.mockRejectedValue(new ApiError({ code: "INTERNAL_ERROR", message: "INTERNAL_ERROR", status: 500 }));
    render(<DefectList />, { wrapper });

    expect(await screen.findByText("服务器处理请求时发生错误，请稍后重试。")).toBeInTheDocument();
  });
});
