import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { DeploymentView, ReleaseView } from "@/features/release";
import { ReleaseWorkspaceView } from "./release-workspace";

const release: ReleaseView = {
  id: 18,
  organizationId: 2,
  versionName: "v0.2.0-rc1",
  environment: "staging",
  status: "PRECHECKED",
  releaseNote: "## 新增\n\n- 确定性发布预检\n- Release Note 编辑",
  itemIds: [30],
  version: 4,
  createdAt: "2026-09-09T10:42:00Z",
  updatedAt: "2026-09-10T01:15:00Z",
  latestPrecheck: {
    id: 22,
    status: "FAIL",
    current: true,
    checkedAt: "2026-09-10T01:15:00Z",
    checkedByType: "USER",
    checkedById: 6,
    resourceVersions: { release: 4, "requirement:30": 8 },
    checks: [
      { rule: "WORK_ITEMS_READY", passed: true, details: [] },
      { rule: "PIPELINE_GREEN", passed: true, details: [] },
      { rule: "QA_PASSED", passed: false, details: ["latest test run failed"] },
      { rule: "NO_BLOCKING_BUGS", passed: false, details: ["BUG-7"] },
      { rule: "ARTIFACTS_PRESENT", passed: true, details: [] },
      { rule: "APPROVAL_POLICY", passed: true, details: [] },
    ],
  },
};

afterEach(cleanup);

describe("ReleaseWorkspaceView", () => {
  it("按视觉稿层级展示后端 Release 与六项确定性预检", () => {
    render(
      <ReleaseWorkspaceView
        requirement={{ itemKey: "DEMO-1", title: "发布闭环", status: "READY_FOR_RELEASE" }}
        release={release}
        deployments={[]}
        busy={false}
        noteDraft={release.releaseNote}
        onNoteChange={vi.fn()}
        onCreate={vi.fn()}
        onSaveNote={vi.fn()}
        onPrecheck={vi.fn()}
        onRequestDeployment={vi.fn()}
        onDecide={vi.fn()}
      />,
    );

    expect(screen.getByRole("heading", { name: "发布管理" })).toBeInTheDocument();
    expect(screen.getByText("确定性预检查 Precheck")).toBeInTheDocument();
    expect(screen.getAllByRole("row")).toHaveLength(7);
    expect(screen.getByText("QA 门禁通过")).toBeInTheDocument();
    expect(screen.getByText("BUG-7")).toBeInTheDocument();
    expect(screen.getAllByText("SIMULATED").length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "申请 HIGH 审批并模拟部署" })).toBeDisabled();
  });

  it("通过当前预检后开放模拟部署并提交真实动作", () => {
    const requestDeployment = vi.fn();
    const passed = {
      ...release,
      status: "READY_FOR_APPROVAL" as const,
      latestPrecheck: {
        ...release.latestPrecheck!,
        status: "PASS" as const,
        checks: release.latestPrecheck!.checks.map((check) => ({ ...check, passed: true, details: [] })),
      },
    };
    render(
      <ReleaseWorkspaceView
        requirement={{ itemKey: "DEMO-1", title: "发布闭环", status: "READY_FOR_RELEASE" }}
        release={passed}
        deployments={[]}
        busy={false}
        noteDraft={passed.releaseNote}
        onNoteChange={vi.fn()}
        onCreate={vi.fn()}
        onSaveNote={vi.fn()}
        onPrecheck={vi.fn()}
        onRequestDeployment={requestDeployment}
        onDecide={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "申请 HIGH 审批并模拟部署" }));
    expect(requestDeployment).toHaveBeenCalledOnce();
  });

  it("展示真实审批记录并允许明确批准或拒绝", () => {
    const decide = vi.fn();
    const deployment: DeploymentView = {
      id: 9,
      releaseId: 18,
      mode: "SIMULATED",
      status: "PENDING_APPROVAL",
      requestedBy: 3,
      approverUserId: null,
      approvalExpiresAt: "2026-09-10T03:00:00Z",
      resultCode: null,
      resultSummary: null,
      version: 0,
    };
    render(
      <ReleaseWorkspaceView
        requirement={{ itemKey: "DEMO-1", title: "发布闭环", status: "READY_FOR_RELEASE" }}
        release={release}
        deployments={[deployment]}
        busy={false}
        noteDraft={release.releaseNote}
        onNoteChange={vi.fn()}
        onCreate={vi.fn()}
        onSaveNote={vi.fn()}
        onPrecheck={vi.fn()}
        onRequestDeployment={vi.fn()}
        onDecide={decide}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "批准" }));
    fireEvent.click(screen.getByRole("button", { name: "拒绝" }));
    expect(decide).toHaveBeenNthCalledWith(1, deployment, "APPROVE");
    expect(decide).toHaveBeenNthCalledWith(2, deployment, "REJECT");
  });
});
