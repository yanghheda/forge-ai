import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
  listTestCases: vi.fn(),
  getLatestTestRun: vi.fn(),
  listBugs: vi.fn(),
  createTestCase: vi.fn(),
  createTestRun: vi.fn(),
  updateTestResult: vi.fn(),
  completeTestRun: vi.fn(),
  reopenTestRun: vi.fn(),
  createBug: vi.fn(),
  transitionBug: vi.fn(),
}));

vi.mock("../api/work-item-api", () => api);
vi.mock("@/features/auth", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/auth")>()),
  getCurrentUser: vi.fn().mockResolvedValue({ id: 6, organization: { id: 0, roles: ["QA"] } }),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

import { BugList, QaPanel } from "./qa-panel";

const wrapper = ({ children }: { children: React.ReactNode }) => <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;

describe("QaPanel", () => {
  it("移除测试运行模块并可查看测试用例详情", async () => {
    api.listTestCases.mockResolvedValue([{ id: 1, caseKey: "QA-CASE-001", requirementId: 2, title: "真实登录回归", preconditions: "", steps: ["登录"], expectedResult: "成功", priority: "P0", caseType: "REGRESSION", createdBy: 6, createdByName: "赵测试", version: 0 }]);
    api.getLatestTestRun.mockResolvedValue({
      id: 9,
      requirementId: 2,
      environment: "staging",
      startedBy: 6,
      startedByName: "赵测试",
      triggerSource: "USER",
      status: "COMPLETED",
      summary: { total: 1, passed: 1, failed: 0, blocked: 0, skipped: 0, notRun: 0, mandatorySkipped: 0 },
      decision: { passed: true, missing: [] },
      results: [{ id: 3, testCaseId: 1, title: "真实登录回归", priority: "P0", status: "PASS", actualResult: "成功", evidence: [], version: 1 }],
      version: 2,
    });
    api.listBugs.mockResolvedValue([{ id: 7, itemKey: "BUG-7", title: "真实接口缺陷", status: "OPEN", severity: "MAJOR", requirementId: 2, testRunId: 9, testResultId: 3, reproductionSteps: ["登录"], expectedResult: "成功", actualResult: "失败", fixNote: null, fixEvidence: [], version: 0 }]);

    render(<QaPanel organizationId={0} requirementId={2} onChanged={async () => undefined} />, { wrapper });

    expect(await screen.findByRole("region", { name: "测试统计" })).toBeInTheDocument();
    expect(screen.getByText("用例总数")).toBeInTheDocument();
    expect(screen.getByText("QA-CASE-001")).toBeInTheDocument();
    expect(screen.getByText("QA 门禁（进入待发布条件）")).toBeInTheDocument();
    expect(screen.getByText("真实登录回归")).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: /测试运行/ })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "查看用例 QA-CASE-001" }));
    expect(screen.getByRole("dialog", { name: "测试用例详情" })).toBeInTheDocument();
    expect(screen.getByText("前置条件")).toBeInTheDocument();
    expect(screen.getByText("预期结果")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "缺陷 1" }));
    expect(screen.getByText("BUG-7")).toBeInTheDocument();
    expect(screen.getByText("真实接口缺陷")).toBeInTheDocument();
  });

  it("用例列表承载人工执行入口与执行中状态", async () => {
    api.listTestCases.mockResolvedValue([{ id: 1, caseKey: "QA-CASE-001", requirementId: 2, title: "登录回归", preconditions: "", steps: ["登录"], expectedResult: "成功", priority: "P1", caseType: "REGRESSION", createdBy: 6, createdByName: "赵测试", version: 0 }]);
    api.getLatestTestRun.mockResolvedValue({
      id: 9,
      requirementId: 2,
      environment: "staging",
      startedBy: 6,
      startedByName: "赵测试",
      triggerSource: "USER",
      status: "IN_PROGRESS",
      summary: null,
      decision: { passed: false, missing: ["completedTestRun"] },
      results: [{ id: 3, testCaseId: 1, title: "登录回归", priority: "P1", status: "IN_PROGRESS", actualResult: "", evidence: [], version: 1 }],
      version: 1,
    });
    api.listBugs.mockResolvedValue([]);

    render(<QaPanel organizationId={0} requirementId={2} onChanged={async () => undefined} />, { wrapper });

    expect(await screen.findByText("执行中")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "完成本轮测试" })).toBeDisabled();
    fireEvent.click(screen.getByRole("tab", { name: "缺陷 0" }));
    expect(screen.getByRole("tab", { name: "缺陷 0" })).toHaveAttribute("aria-selected", "true");
    fireEvent.click(screen.getByRole("button", { name: "创建缺陷" }));
    expect(screen.getByRole("dialog", { name: "创建缺陷" })).toBeInTheDocument();
    expect(screen.getByText("复现步骤（每行一步）")).toBeInTheDocument();
  });

  it("没有测试轮次时可直接选择执行中并自动创建后台轮次", async () => {
    const testCase = { id: 1, caseKey: "QA-CASE-001", requirementId: 2, title: "登录回归", preconditions: "", steps: ["登录"], expectedResult: "成功", priority: "P1", caseType: "REGRESSION", createdBy: 6, createdByName: "赵测试", version: 0 };
    api.listTestCases.mockResolvedValue([testCase]);
    api.getLatestTestRun.mockResolvedValue(null);
    api.listBugs.mockResolvedValue([]);
    api.createTestRun.mockResolvedValue({
      id: 10,
      requirementId: 2,
      environment: "staging",
      startedBy: 6,
      startedByName: "赵测试",
      triggerSource: "USER",
      status: "DRAFT",
      summary: null,
      decision: { passed: false, missing: ["completedTestRun"] },
      results: [{ id: 4, testCaseId: 1, title: "登录回归", priority: "P1", status: "NOT_RUN", actualResult: "", evidence: [], version: 0 }],
      version: 0,
    });
    api.updateTestResult.mockResolvedValue({ id: 4, testCaseId: 1, status: "IN_PROGRESS", version: 1 });
    const user = userEvent.setup({ pointerEventsCheck: 0 });

    render(<QaPanel organizationId={0} requirementId={2} onChanged={async () => undefined} />, { wrapper });

    const select = await screen.findByRole("combobox", { name: "登录回归 结果" });
    await user.click(select);
    await user.click(await screen.findByRole("option", { name: "执行中" }));

    await waitFor(() => expect(api.createTestRun).toHaveBeenCalledWith({ organizationId: 0, requirementId: 2, environment: "staging" }));
    expect(api.updateTestResult).toHaveBeenCalledWith({ organizationId: 0, runId: 10, resultId: 4, status: "IN_PROGRESS", expectedVersion: 0 });
  });
});

describe("BugList", () => {
  it("按 Bug 状态提供动作并可查看详情", () => {
    const action = vi.fn();
    render(
      <BugList
        bugs={[
          {
            id: 9,
            itemKey: "QA-9",
            title: "支付失败",
            status: "RESOLVED",
            severity: "BLOCKER",
            requirementId: 2,
            testRunId: 1,
            testResultId: 3,
            devTaskId: 8,
            devTaskKey: "DEV-8",
            assigneeUserId: 6,
            assigneeName: "赵测试",
            reproductionSteps: ["提交订单"],
            expectedResult: "成功",
            actualResult: "HTTP 500",
            fixNote: "修复空值",
            fixEvidence: ["mr://42"],
            version: 2,
          },
        ]}
        onAction={action}
      />,
    );

    expect(screen.getByText("BLOCKER")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "查看缺陷 QA-9" }));
    expect(screen.getByRole("dialog", { name: "缺陷详情" })).toBeInTheDocument();
    expect(screen.getByText("HTTP 500")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "验证" }));
    expect(action).toHaveBeenCalledWith(expect.objectContaining({ id: 9 }), "VERIFY", undefined, undefined);
  });
});
