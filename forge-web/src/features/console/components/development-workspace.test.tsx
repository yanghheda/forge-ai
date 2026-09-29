import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { DevelopmentWorkspaceView } from "./development-workspace";

const summary = {
  requirementId: 30,
  ciRequired: true,
  repositoryConfigured: true,
  tasks: [
    {
      id: 40,
      itemKey: "DEMO-2",
      title: "实现 Agent 审批闭环",
      status: "IN_PROGRESS",
      version: 3,
      branchName: "feature/demo-2-agent-review",
      branchCommitSha: "8f2a1c4",
      mergeRequestId: 12,
      mergeRequestUrl: "https://gitlab.example/mr/12",
      mergeRequestState: "merged",
      mergeRequestHeadSha: "8f2a1c4",
      pipelineId: 109,
      pipelineCommitSha: "8f2a1c4",
      pipelineStatus: "running",
      pipelineLastSyncedAt: "2026-09-10T14:21:00Z",
    },
  ],
};

// 两个 Dev Task 各自持有独立的源分支、MR 与 Pipeline，第二个任务的 Pipeline 失败。
const twoTasks = {
  ...summary,
  tasks: [
    { ...summary.tasks[0], status: "DONE", pipelineStatus: "success" },
    {
      ...summary.tasks[0],
      id: 41,
      itemKey: "DEMO-3",
      title: "接入导出任务队列",
      version: 1,
      branchName: "feature/demo-3-export-queue",
      mergeRequestId: 13,
      mergeRequestUrl: "https://gitlab.example/mr/13",
      mergeRequestHeadSha: "b7c1d90",
      pipelineCommitSha: "b7c1d90",
      pipelineId: 118,
      pipelineStatus: "failed",
    },
  ],
};

afterEach(cleanup);

describe("DevelopmentWorkspaceView", () => {
  it("按视觉稿的信息层级展示真实开发汇总并触发同步", () => {
    const refresh = vi.fn();
    render(<DevelopmentWorkspaceView summary={summary} workflow={{ version: 5, availableActions: [], guardHints: { SUBMIT_FOR_QA: ["pipelineSuccess"] } }} onTriggerPipeline={vi.fn()} onRefresh={refresh} />);

    expect(screen.getByRole("heading", { name: "开发" })).toBeInTheDocument();
    expect(screen.getByText("Dev Task 交付链路")).toBeInTheDocument();
    expect(screen.getAllByText("Pipeline #109").length).toBeGreaterThan(0);
    expect(screen.getAllByText("feature/demo-2-agent-review").length).toBeGreaterThan(0);
    expect(screen.getByText("pipelineSuccess")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "提交验证" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "同步状态" }));
    expect(refresh).toHaveBeenCalledOnce();
  });

  it("可按任务分支重新触发 Pipeline", () => {
    const trigger = vi.fn();
    const passed = { ...summary, tasks: [{ ...summary.tasks[0], pipelineStatus: "success" }] };
    render(<DevelopmentWorkspaceView summary={passed} workflow={{ version: 5, availableActions: [], guardHints: {} }} onTriggerPipeline={trigger} onRefresh={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "重新触发 Pipeline" }));
    expect(trigger).toHaveBeenCalledWith(40, "feature/demo-2-agent-review");
  });

  it("任务流转入口不在此工作区，只保留交付物展示与操作", () => {
    const opened = { ...summary, tasks: [{ ...summary.tasks[0], pipelineStatus: "success", mergeRequestState: "opened" }] };
    render(<DevelopmentWorkspaceView summary={opened} workflow={{ version: 5, availableActions: ["SUBMIT_FOR_QA"], guardHints: {} }} onTriggerPipeline={vi.fn()} onRefresh={vi.fn()} />);

    expect(screen.getByText("合并请求 · 待合并")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "提交验证" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "启动开发" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "查看 MR" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重新触发 Pipeline" })).toBeInTheDocument();
  });

  it("开发门禁通过后不再提供推进 QA 按钮（推进流程已统一到需求详情页）", () => {
    const passed = { ...summary, tasks: [{ ...summary.tasks[0], status: "DONE", pipelineStatus: "success" }] };
    render(<DevelopmentWorkspaceView summary={passed} workflow={{ version: 5, availableActions: ["SUBMIT_FOR_QA"], guardHints: {} }} onTriggerPipeline={vi.fn()} onRefresh={vi.fn()} />);

    expect(screen.queryByRole("button", { name: "提交开发评审" })).not.toBeInTheDocument();
    expect(screen.getByText("已通过")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "推进到 QA" })).not.toBeInTheDocument();
  });

  it("门禁事实通过时不误报为门禁失败", () => {
    const passed = { ...summary, tasks: [{ ...summary.tasks[0], status: "DONE", pipelineStatus: "success" }] };
    render(<DevelopmentWorkspaceView summary={passed} workflow={{ version: 5, availableActions: [], guardHints: {} }} onTriggerPipeline={vi.fn()} onRefresh={vi.fn()} />);

    expect(screen.getByText("已通过")).toBeInTheDocument();
  });

  it("已提交验证仍可补触发 Pipeline", () => {
    const trigger = vi.fn();
    const completed = { ...summary, tasks: [{ ...summary.tasks[0], status: "DONE", pipelineId: null, pipelineStatus: null }] };
    render(<DevelopmentWorkspaceView summary={completed} workflow={{ version: 5, availableActions: [], guardHints: {} }} onTriggerPipeline={trigger} onRefresh={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "触发 Pipeline" }));
    expect(trigger).toHaveBeenCalledWith(40, "feature/demo-2-agent-review");
  });

  it("多任务时按 Dev Task 分组展示各自的源分支、MR 与 Pipeline，并逐任务校验门禁", () => {
    render(<DevelopmentWorkspaceView summary={twoTasks} workflow={{ version: 5, availableActions: ["SUBMIT_FOR_QA"], guardHints: {} }} onTriggerPipeline={vi.fn()} onRefresh={vi.fn()} />);

    expect(screen.getByText("feature/demo-2-agent-review")).toBeInTheDocument();
    expect(screen.getByText("feature/demo-3-export-queue")).toBeInTheDocument();
    expect(screen.getByText("!12")).toBeInTheDocument();
    expect(screen.getByText("!13")).toBeInTheDocument();
    expect(screen.getAllByText("合并请求 · 已合并")).toHaveLength(2);
    expect(screen.getByText("Pipeline #109")).toBeInTheDocument();
    expect(screen.getByText("Pipeline #118")).toBeInTheDocument();
    expect(screen.getByText(/Pipeline #118 失败/)).toBeInTheDocument();
    expect(screen.getByText("未通过")).toBeInTheDocument();
  });

  it("只有被操作的那个任务的按钮进入加载态", () => {
    render(<DevelopmentWorkspaceView summary={twoTasks} pending={{ action: "pipeline", taskId: 41 }} workflow={{ version: 5, availableActions: [], guardHints: {} }} onTriggerPipeline={vi.fn()} onRefresh={vi.fn()} />);

    const triggerButtons = screen.getAllByRole("button", { name: "重新触发 Pipeline" });
    expect(triggerButtons).toHaveLength(2);
    expect(triggerButtons[0]).not.toHaveClass("arco-btn-loading");
    expect(triggerButtons[1]).toHaveClass("arco-btn-loading");
  });

  it("GitLab 面板不再提供缺少执行人分配的旧创建入口", () => {
    render(<DevelopmentWorkspaceView summary={{ ...summary, tasks: [] }} workflow={{ version: 5, availableActions: [], guardHints: {} }} onTriggerPipeline={vi.fn()} onRefresh={vi.fn()} />);
    expect(screen.queryByRole("button", { name: "新建 Dev Task" })).not.toBeInTheDocument();
  });

  it("非研发角色只读：隐藏新建与任务操作", () => {
    render(<DevelopmentWorkspaceView summary={summary} workflow={{ version: 5, availableActions: [], guardHints: {} }} readOnly onTriggerPipeline={vi.fn()} onRefresh={vi.fn()} />);

    expect(screen.queryByRole("button", { name: "新建 Dev Task" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "重新触发 Pipeline" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "提交验证" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "同步状态" })).toBeInTheDocument();
  });
});
