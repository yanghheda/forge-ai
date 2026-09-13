import { cleanup, fireEvent, render, screen } from "@testing-library/react";
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

afterEach(cleanup);

import { BugList, QaPanel, QaRunView } from "./qa-panel";

const wrapper = ({ children }: { children: React.ReactNode }) => <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;

describe("QaPanel", () => {
  it("展示 Server 返回的用例、最新执行统计与关联 Bug", async () => {
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
    fireEvent.click(screen.getByRole("tab", { name: "测试运行 1" }));
    expect(screen.getByText("测试运行 RUN-9")).toBeInTheDocument();
    expect(screen.getByText("真实登录回归", { exact: false })).toBeInTheDocument();
    expect(screen.getByText("通过 1/1；允许 QA 通过")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "缺陷 1" }));
    expect(screen.getByText("BUG-7")).toBeInTheDocument();
    expect(screen.getByText("真实接口缺陷")).toBeInTheDocument();
  });

  it("可切换测试用例、测试运行和缺陷视图", async () => {
    api.listTestCases.mockResolvedValue([{ id: 1, caseKey: "QA-CASE-001", requirementId: 2, title: "登录回归", preconditions: "", steps: ["登录"], expectedResult: "成功", priority: "P1", caseType: "REGRESSION", createdBy: 6, createdByName: "赵测试", version: 0 }]);
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
      results: [{ id: 3, testCaseId: 1, title: "登录回归", priority: "P1", status: "PASS", actualResult: "", evidence: [], version: 1 }],
      version: 2,
    });
    api.listBugs.mockResolvedValue([]);

    render(<QaPanel organizationId={0} requirementId={2} onChanged={async () => undefined} />, { wrapper });

    expect(await screen.findByRole("tab", { name: "测试用例" })).toHaveAttribute("aria-selected", "true");
    fireEvent.click(screen.getByRole("tab", { name: "测试运行 1" }));
    expect(screen.getByRole("tab", { name: "测试运行 1" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("测试运行 RUN-9")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "缺陷 0" }));
    expect(screen.getByRole("tab", { name: "缺陷 0" })).toHaveAttribute("aria-selected", "true");
    expect(screen.queryByText("测试运行 RUN-9")).not.toBeInTheDocument();
  });
});

describe("QaRunView", () => {
  it("展示固化统计且已完成 Run 只能 reopen", () => {
    const reopen = vi.fn();
    render(
      <QaRunView
        run={{
          id: 1,
          requirementId: 2,
          environment: "staging",
          startedBy: 6,
          startedByName: "赵测试",
          triggerSource: "USER",
          status: "COMPLETED",
          summary: {
            total: 2,
            passed: 1,
            failed: 0,
            blocked: 0,
            skipped: 1,
            notRun: 0,
            mandatorySkipped: 0,
          },
          decision: { passed: true, missing: [] },
          results: [
            {
              id: 3,
              testCaseId: 4,
              title: "核心路径",
              priority: "P0",
              status: "PASS",
              actualResult: "",
              evidence: [],
              version: 1,
            },
          ],
          version: 2,
        }}
        busy={false}
        onResult={vi.fn()}
        onComplete={vi.fn()}
        onReopen={reopen}
      />,
    );

    expect(screen.getByText("通过 1/2；允许 QA 通过")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "完成测试执行" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "重新打开测试执行" }));
    expect(reopen).toHaveBeenCalledOnce();
  });

  it("已完成运行可以基于当前用例创建新一轮执行", () => {
    const createNewRun = vi.fn();
    render(
      <QaRunView
        run={{
          id: 1,
          requirementId: 2,
          environment: "staging",
          startedBy: 6,
          startedByName: "赵测试",
          triggerSource: "USER",
          status: "COMPLETED",
          summary: { total: 1, passed: 0, failed: 1, blocked: 0, skipped: 0, notRun: 0, mandatorySkipped: 0 },
          decision: { passed: false, missing: ["FAILED_RESULTS"] },
          results: [{ id: 3, testCaseId: 4, title: "核心路径", priority: "P0", status: "FAIL", actualResult: "失败", evidence: [], version: 1 }],
          version: 2,
        }}
        busy={false}
        onResult={vi.fn()}
        onComplete={vi.fn()}
        onReopen={vi.fn()}
        onCreateNewRun={createNewRun}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "创建新测试执行" }));
    expect(createNewRun).toHaveBeenCalledOnce();
  });
});

describe("BugList", () => {
  it("按 Bug 状态提供修复与独立验证动作", () => {
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
    fireEvent.click(screen.getByRole("button", { name: "验证" }));
    expect(action).toHaveBeenCalledWith(expect.objectContaining({ id: 9 }), "VERIFY", undefined, undefined);
  });
});
