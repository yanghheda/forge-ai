import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { BugList, QaRunView } from "./qa-panel";

describe("QaRunView", () => {
  it("展示固化统计且已完成 Run 只能 reopen", () => {
    const reopen = vi.fn();
    render(
      <QaRunView
        run={{
          id: 1,
          requirementId: 2,
          environment: "staging",
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
