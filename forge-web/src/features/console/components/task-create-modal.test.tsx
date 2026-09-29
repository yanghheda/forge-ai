import { Message } from "@arco-design/web-react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { TaskCreationOption } from "../api/console-api";

const api = vi.hoisted(() => ({
  createStageTask: vi.fn(),
  listTaskCreationOptions: vi.fn(),
}));

vi.mock("@/features/work-item", () => ({ createStageTask: api.createStageTask }));
vi.mock("../api/console-api", () => ({ listTaskCreationOptions: api.listTaskCreationOptions }));

import { TaskCreateModal } from "./task-create-modal";

const creationOptions: TaskCreationOption[] = [
  {
    requirementId: 1,
    requirementItemKey: "REQ-1",
    requirementTitle: "登录页改版",
    taskType: "UX_TASK",
    executors: [{ userId: 5, displayName: "李设计" }],
  },
  {
    requirementId: 2,
    requirementItemKey: "REQ-2",
    requirementTitle: "验证码登录",
    taskType: "DEV_TASK",
    executors: [],
  },
];

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;
}

/* 选择需求下拉中的指定选项。 */
async function selectRequirement(label: string) {
  const user = userEvent.setup();
  await user.click(screen.getByRole("combobox", { name: "需求" }));
  fireEvent.click(await screen.findByText(label));
}

describe("TaskCreateModal", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    api.listTaskCreationOptions.mockResolvedValue(creationOptions);
    api.createStageTask.mockResolvedValue({});
    vi.spyOn(Message, "error").mockImplementation(() => () => undefined);
  });

  it("只能选择该需求已关联的执行人并提交创建", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<TaskCreateModal visible onClose={onClose} />, { wrapper });

    await selectRequirement("REQ-1 登录页改版 · 设计任务");
    await user.click(screen.getByRole("combobox", { name: "执行人" }));
    expect(await screen.findByRole("option", { name: "李设计" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("option", { name: "李设计" }));
    await user.type(screen.getByLabelText("任务标题"), "登录流程设计稿");
    await user.click(screen.getByRole("button", { name: "创建任务" }));

    await waitFor(() => expect(api.createStageTask).toHaveBeenCalledWith(1, { type: "UX_TASK", title: "登录流程设计稿", assigneeUserId: 5 }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it("未选需求、执行人或标题时禁用提交", async () => {
    render(<TaskCreateModal visible onClose={vi.fn()} />, { wrapper });

    expect(screen.getByRole("button", { name: "创建任务" })).toBeDisabled();

    await selectRequirement("REQ-1 登录页改版 · 设计任务");
    expect(screen.getByRole("button", { name: "创建任务" })).toBeDisabled();
  });

  it("需求未关联本阶段执行人时提示且不可提交", async () => {
    render(<TaskCreateModal visible onClose={vi.fn()} />, { wrapper });

    await selectRequirement("REQ-2 验证码登录 · 开发任务");

    /* Arco Select 会同时渲染可见镜像与隐藏值节点，这里断言文案存在即可。 */
    expect(screen.getAllByText("该需求尚未关联本阶段执行人").length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "创建任务" })).toBeDisabled();
  });

  it("没有可创建任务的需求时展示空态", async () => {
    api.listTaskCreationOptions.mockResolvedValue([]);
    const user = userEvent.setup();
    render(<TaskCreateModal visible onClose={vi.fn()} />, { wrapper });

    await user.click(screen.getByRole("combobox", { name: "需求" }));

    expect(await screen.findByText("当前没有可创建任务的需求")).toBeInTheDocument();
  });
});
