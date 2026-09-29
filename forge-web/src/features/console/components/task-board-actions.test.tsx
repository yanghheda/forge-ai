import { Message } from "@arco-design/web-react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { BoardItem } from "../api/console-api";

const api = vi.hoisted(() => ({
  transitionTask: vi.fn(),
  startDevelopment: vi.fn(),
  completeDevTask: vi.fn(),
}));

vi.mock("@/features/work-item", () => ({
  transitionTask: api.transitionTask,
  startDevelopment: api.startDevelopment,
  completeDevTask: api.completeDevTask,
}));

import { TaskBoardActions } from "./task-board-actions";

const boardItem = (overrides: Partial<BoardItem>): BoardItem => ({
  id: 11,
  itemKey: "REQ-1-DEV-1",
  title: "登录接口",
  type: "DEV_TASK",
  requirementId: 1,
  lane: "TODO",
  position: 1000,
  assigneeUserId: 2,
  priority: "MEDIUM",
  dueAt: null,
  assigneeName: "王开发",
  blockedReason: null,
  version: 3,
  availableActions: [],
  guardHints: {},
  ...overrides,
});

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;
}

/* 打开卡片动作下拉并点击目标动作。 */
async function clickAction(label: string) {
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "任务操作：REQ-1-DEV-1" }));
  fireEvent.click(await screen.findByText(label));
}

describe("TaskBoardActions", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    api.transitionTask.mockResolvedValue(undefined);
    api.startDevelopment.mockResolvedValue(undefined);
    api.completeDevTask.mockResolvedValue(undefined);
    vi.spyOn(Message, "error").mockImplementation(() => () => undefined);
  });

  it("开发任务开工走启动开发接口", async () => {
    render(<TaskBoardActions item={boardItem({ availableActions: ["START"] })} />, { wrapper });

    await clickAction("开始处理");

    await waitFor(() => expect(api.startDevelopment).toHaveBeenCalledTimes(1));
    expect(api.startDevelopment.mock.calls[0][0]).toMatchObject({ taskId: 11 });
    expect(api.transitionTask).not.toHaveBeenCalled();
  });

  it("开发任务提交验证走完成开发任务接口并带上版本", async () => {
    render(<TaskBoardActions item={boardItem({ lane: "IN_PROGRESS", availableActions: ["SUBMIT_REVIEW", "BLOCK"] })} />, { wrapper });

    await clickAction("提交验证");

    await waitFor(() => expect(api.completeDevTask).toHaveBeenCalledWith(expect.objectContaining({ taskId: 11, expectedVersion: 3 })));
    expect(api.transitionTask).not.toHaveBeenCalled();
  });

  it("测试任务提交验证走通用流转", async () => {
    render(<TaskBoardActions item={boardItem({ id: 12, itemKey: "REQ-1-QA-1", type: "QA_TASK", lane: "IN_PROGRESS", availableActions: ["SUBMIT_REVIEW"] })} />, { wrapper });

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "任务操作：REQ-1-QA-1" }));
    fireEvent.click(await screen.findByText("提交验证"));

    await waitFor(() => expect(api.transitionTask).toHaveBeenCalledWith(12, "SUBMIT_REVIEW", 3, undefined));
    expect(api.completeDevTask).not.toHaveBeenCalled();
  });

  it("退回修改必须填写原因后才提交带原因的流转", async () => {
    const user = userEvent.setup();
    render(<TaskBoardActions item={boardItem({ type: "QA_TASK", lane: "WAITING_VERIFICATION", availableActions: ["APPROVE", "REJECT"] })} />, { wrapper });

    await clickAction("退回修改");

    const confirm = await screen.findByRole("button", { name: "提交" });
    expect(confirm).toBeDisabled();
    await user.type(screen.getByLabelText("任务操作原因"), "验收用例未覆盖");
    expect(confirm).toBeEnabled();
    await user.click(confirm);

    await waitFor(() => expect(api.transitionTask).toHaveBeenCalledWith(11, "REJECT", 3, "验收用例未覆盖"));
  });

  it("仅缺少原因的动作不被禁用，也不在卡片上提示", async () => {
    render(<TaskBoardActions item={boardItem({ lane: "IN_PROGRESS", availableActions: ["SUBMIT_REVIEW", "BLOCK"], guardHints: { BLOCK: ["reason"] } })} />, { wrapper });

    expect(screen.queryByText(/还缺/)).not.toBeInTheDocument();

    await clickAction("标记阻塞");

    expect(await screen.findByRole("button", { name: "提交" })).toBeInTheDocument();
    expect(api.transitionTask).not.toHaveBeenCalled();
  });

  it("缺少阶段准入材料时禁用动作并展示提示", async () => {
    render(<TaskBoardActions item={boardItem({ availableActions: ["START"], guardHints: { START: ["activeTaskStage"] } })} />, { wrapper });

    expect(screen.getByText("还缺：需求进入对应执行阶段")).toBeInTheDocument();

    await clickAction("开始处理");

    expect(api.startDevelopment).not.toHaveBeenCalled();
    expect(api.transitionTask).not.toHaveBeenCalled();
  });

  it("缺陷卡片不渲染任务操作区", () => {
    render(<TaskBoardActions item={boardItem({ type: "BUG" })} />, { wrapper });

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
