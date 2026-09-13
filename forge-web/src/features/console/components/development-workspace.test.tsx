import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
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
      mergeRequestHeadSha: "8f2a1c4",
      pipelineId: 109,
      pipelineCommitSha: "8f2a1c4",
      pipelineStatus: "running",
      pipelineLastSyncedAt: "2026-09-10T14:21:00Z",
    },
  ],
};

afterEach(cleanup);

describe("DevelopmentWorkspaceView", () => {
  it("按视觉稿的信息层级展示真实开发汇总并触发同步与任务完成", () => {
    const complete = vi.fn();
    const refresh = vi.fn();
    render(<DevelopmentWorkspaceView summary={summary} workflow={{ version: 5, availableActions: [], guardHints: { SUBMIT_FOR_QA: ["pipelineSuccess"] } }} busy={false} onCreate={vi.fn()} onStart={vi.fn()} onTriggerPipeline={vi.fn()} onComplete={complete} onRefresh={refresh} onSubmitQa={vi.fn()} />);

    expect(screen.getByRole("heading", { name: "开发" })).toBeInTheDocument();
    expect(screen.getByText("分支与合并请求")).toBeInTheDocument();
    expect(screen.getAllByText("Pipeline #109").length).toBeGreaterThan(0);
    expect(screen.getAllByText("feature/demo-2-agent-review").length).toBeGreaterThan(0);
    expect(screen.getByText("pipelineSuccess")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "同步状态" }));
    expect(screen.getByRole("button", { name: "完成任务" })).toBeDisabled();
    expect(refresh).toHaveBeenCalledOnce();
    expect(complete).not.toHaveBeenCalled();
  });

  it("Pipeline 成功后允许完成任务，并可按任务分支重新触发", () => {
    const complete = vi.fn();
    const trigger = vi.fn();
    const passed = { ...summary, tasks: [{ ...summary.tasks[0], pipelineStatus: "success" }] };
    render(<DevelopmentWorkspaceView summary={passed} workflow={{ version: 5, availableActions: [], guardHints: {} }} busy={false} onCreate={vi.fn()} onStart={vi.fn()} onTriggerPipeline={trigger} onComplete={complete} onRefresh={vi.fn()} onSubmitQa={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "重新触发 Pipeline" }));
    fireEvent.click(screen.getByRole("button", { name: "完成任务" }));
    expect(trigger).toHaveBeenCalledWith(40, "feature/demo-2-agent-review");
    expect(complete).toHaveBeenCalledWith(40, 3);
  });

  it("开发门禁通过后只展示推进 QA 动作", () => {
    const submitQa = vi.fn();
    const passed = { ...summary, tasks: [{ ...summary.tasks[0], status: "DONE", pipelineStatus: "success" }] };
    render(<DevelopmentWorkspaceView summary={passed} workflow={{ version: 5, availableActions: ["SUBMIT_FOR_QA"], guardHints: {} }} busy={false} onCreate={vi.fn()} onStart={vi.fn()} onTriggerPipeline={vi.fn()} onComplete={vi.fn()} onRefresh={vi.fn()} onSubmitQa={submitQa} />);

    expect(screen.queryByRole("button", { name: "提交开发评审" })).not.toBeInTheDocument();
    expect(screen.getByText("已通过")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "推进到 QA" }));
    expect(submitQa).toHaveBeenCalledOnce();
  });

  it("门禁事实通过但动作不可用时不误报为门禁失败", () => {
    const passed = { ...summary, tasks: [{ ...summary.tasks[0], status: "DONE", pipelineStatus: "success" }] };
    render(<DevelopmentWorkspaceView summary={passed} workflow={{ version: 5, availableActions: [], guardHints: {} }} busy={false} onCreate={vi.fn()} onStart={vi.fn()} onTriggerPipeline={vi.fn()} onComplete={vi.fn()} onRefresh={vi.fn()} onSubmitQa={vi.fn()} />);

    expect(screen.getByText("已通过")).toBeInTheDocument();
    expect(screen.getByText("当前账号无推进权限，或需求已不处于开发中。")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "推进到 QA" })).toBeDisabled();
  });

  it("已完成任务仍可补触发 Pipeline", () => {
    const trigger = vi.fn();
    const completed = { ...summary, tasks: [{ ...summary.tasks[0], status: "DONE", pipelineId: null, pipelineStatus: null }] };
    render(<DevelopmentWorkspaceView summary={completed} workflow={{ version: 5, availableActions: [], guardHints: {} }} busy={false} onCreate={vi.fn()} onStart={vi.fn()} onTriggerPipeline={trigger} onComplete={vi.fn()} onRefresh={vi.fn()} onSubmitQa={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "触发 Pipeline" }));
    expect(trigger).toHaveBeenCalledWith(40, "feature/demo-2-agent-review");
  });

  it("从 Dev Task 卡片打开抽屉并创建研发任务", async () => {
    const create = vi.fn().mockResolvedValue(undefined);
    render(
      <DevelopmentWorkspaceView
        summary={{ ...summary, tasks: [] }}
        workflow={{ version: 5, availableActions: [], guardHints: {} }}
        busy={false}
        onCreate={create}
        onStart={vi.fn()}
        onTriggerPipeline={vi.fn()}
        onComplete={vi.fn()}
        onRefresh={vi.fn()}
        onSubmitQa={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "新建 Dev Task" }));
    expect(screen.getByLabelText("Dev Task 标题")).toBeInTheDocument();
    const button = screen.getByRole("button", { name: "创建 Dev Task" });
    expect(button).toBeDisabled();
    fireEvent.change(screen.getByLabelText("Dev Task 标题"), { target: { value: "实现登录接口" } });
    fireEvent.change(screen.getByLabelText("Dev Task 说明"), { target: { value: "覆盖验证码和 Session" } });
    fireEvent.click(button);
    await waitFor(() => expect(create).toHaveBeenCalledWith("实现登录接口", "覆盖验证码和 Session"));
  });
});
