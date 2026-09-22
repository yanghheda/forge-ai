import { Message } from "@arco-design/web-react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { BoardItem } from "../api/console-api";

const api = vi.hoisted(() => ({
  getCurrentUser: vi.fn(),
  listRequirementMembers: vi.fn(),
  listBoardItems: vi.fn(),
  listBoardRequirements: vi.fn(),
  moveBoardItem: vi.fn(),
}));

vi.mock("@/features/auth", () => ({ getCurrentUser: api.getCurrentUser }));
vi.mock("@/features/work-item", () => ({ listRequirementMembers: api.listRequirementMembers }));
vi.mock("../api/console-api", () => ({
  listBoardItems: api.listBoardItems,
  listBoardRequirements: api.listBoardRequirements,
  moveBoardItem: api.moveBoardItem,
}));

import { TaskBoard, taskTarget } from "./task-board";

const boardItem = (overrides: Partial<BoardItem>): BoardItem => ({
  id: 1,
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
  version: 0,
  ...overrides,
});

const items: BoardItem[] = [
  boardItem({ id: 1, itemKey: "REQ-1-DEV-1", title: "登录接口", type: "DEV_TASK", requirementId: 1, lane: "TODO", assigneeUserId: 2, priority: "HIGH", dueAt: "2026-09-19T00:00:00Z", assigneeName: "王开发" }),
  boardItem({ id: 2, itemKey: "REQ-1-DEV-2", title: "登录页样式", type: "DEV_TASK", requirementId: 1, lane: "IN_PROGRESS", assigneeUserId: 2, priority: "MEDIUM", assigneeName: "王开发" }),
  boardItem({ id: 3, itemKey: "REQ-2-QA-1", title: "验证码回归用例", type: "QA_TASK", requirementId: 2, lane: "TODO", assigneeUserId: 9, priority: "HIGH", assigneeName: "赵测试" }),
  boardItem({ id: 4, itemKey: "REQ-2-BUG-9", title: "验证码错误提示", type: "BUG", requirementId: 2, lane: "BLOCKED", assigneeUserId: 2, priority: "URGENT", assigneeName: "王开发", blockedReason: "等待上游接口就绪" }),
  boardItem({ id: 5, itemKey: "REQ-1-UX-1", title: "登录流程设计稿", type: "UX_TASK", requirementId: 1, lane: "DONE", assigneeUserId: 9, priority: "LOW", assigneeName: "赵测试" }),
  boardItem({ id: 6, itemKey: "REQ-9-BUG-1", title: "无关联需求的缺陷", type: "BUG", requirementId: null, lane: "TODO", assigneeUserId: 9, priority: "LOW", assigneeName: "赵测试" }),
];

const members = [
  { userId: 2, displayName: "王开发", email: "wang@forge.ai", roles: ["DEVELOPER"] },
  { userId: 9, displayName: "赵测试", email: "zhao@forge.ai", roles: ["QA"] },
];

const requirements = [
  { id: 1, itemKey: "REQ-1", title: "登录页改版" },
  { id: 2, itemKey: "REQ-2", title: "验证码登录" },
];

const currentUser = (roles: string[], id = 2) => ({ id, email: "wang@forge.ai", displayName: "王开发", organization: { id: 1, slug: "demo", name: "Demo", roles } });

function wrapper({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={["/task-board"]}>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

/* 模拟 DataTransfer：dragStart 写入的数据可在 drop 时读回。 */
function fakeDataTransfer() {
  const store = new Map<string, string>();
  return {
    setData: (type: string, value: string) => store.set(type, value),
    getData: (type: string) => store.get(type) ?? "",
  };
}

describe("TaskBoard", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    api.getCurrentUser.mockResolvedValue(currentUser(["DEVELOPER"]));
    api.listRequirementMembers.mockResolvedValue(members);
    api.listBoardItems.mockResolvedValue(items);
    api.listBoardRequirements.mockResolvedValue(requirements);
    api.moveBoardItem.mockResolvedValue(undefined);
    vi.spyOn(Message, "info").mockImplementation(() => () => undefined);
    vi.spyOn(Message, "error").mockImplementation(() => () => undefined);
  });

  it("展示五个真实状态列，且不含评审中", async () => {
    render(<TaskBoard />, { wrapper });

    expect(await screen.findByText("登录接口")).toBeInTheDocument();
    expect(within(screen.getByTestId("column-TODO")).getByText("待处理")).toBeInTheDocument();
    expect(within(screen.getByTestId("column-IN_PROGRESS")).getByText("进行中")).toBeInTheDocument();
    expect(within(screen.getByTestId("column-BLOCKED")).getByText("阻塞中")).toBeInTheDocument();
    expect(within(screen.getByTestId("column-WAITING_VERIFICATION")).getByText("待验证")).toBeInTheDocument();
    expect(within(screen.getByTestId("column-DONE")).getByText("已完成")).toBeInTheDocument();
    expect(within(screen.getByTestId("board-stats")).getByText("阻塞")).toBeInTheDocument();
    expect(screen.queryByText("评审中")).not.toBeInTheDocument();
  });

  it("默认执行人为当前登录用户，只展示自己的任务", async () => {
    render(<TaskBoard />, { wrapper });

    const assignee = await screen.findByRole("combobox", { name: "执行人" });
    expect(assignee).toHaveTextContent("王开发");
    await waitFor(() => expect(api.listBoardRequirements).toHaveBeenCalledWith(2));
    expect(screen.getByText("登录接口")).toBeInTheDocument();
    expect(screen.queryByText("验证码回归用例")).not.toBeInTheDocument();
  });

  it("DEVELOPER 角色下任务标题链接到开发工作区", async () => {
    render(<TaskBoard />, { wrapper });

    expect(await screen.findByRole("link", { name: "登录接口" })).toHaveAttribute("href", "/requirements/1/dev");
  });

  it("切换执行人后按新 userId 拉取需求选项并重置需求筛选", async () => {
    const user = userEvent.setup();
    render(<TaskBoard />, { wrapper });
    await screen.findByText("登录接口");

    await user.click(screen.getByRole("combobox", { name: "执行人" }));
    fireEvent.click(await screen.findByRole("option", { name: "赵测试" }));

    await waitFor(() => expect(api.listBoardRequirements).toHaveBeenCalledWith(9));
    expect(screen.getByRole("combobox", { name: "需求" })).toHaveTextContent("全部");
    expect(await screen.findByText("验证码回归用例")).toBeInTheDocument();
    expect(screen.queryByText("登录接口")).not.toBeInTheDocument();
  });

  it("选择优先级「高」后只保留 HIGH 任务", async () => {
    const user = userEvent.setup();
    render(<TaskBoard />, { wrapper });
    await screen.findByText("登录接口");

    await user.click(screen.getByRole("combobox", { name: "优先级" }));
    fireEvent.click(await screen.findByRole("option", { name: "高" }));

    expect(screen.getByText("登录接口")).toBeInTheDocument();
    expect(screen.queryByText("登录页样式")).not.toBeInTheDocument();
    expect(screen.getByTestId("stat-TODO")).toHaveTextContent("1");
    expect(screen.getByTestId("stat-IN_PROGRESS")).toHaveTextContent("0");
  });

  it("执行人、需求、优先级组合过滤后统计与列计数一致", async () => {
    const user = userEvent.setup();
    render(<TaskBoard />, { wrapper });
    await screen.findByText("登录接口");

    await user.click(screen.getByRole("combobox", { name: "执行人" }));
    fireEvent.click(await screen.findByRole("option", { name: "全部" }));
    await waitFor(() => expect(api.listBoardRequirements).toHaveBeenCalledWith(undefined));

    await user.click(screen.getByRole("combobox", { name: "需求" }));
    fireEvent.click(await screen.findByRole("option", { name: "REQ-2 验证码登录" }));

    await user.click(screen.getByRole("combobox", { name: "优先级" }));
    fireEvent.click(await screen.findByRole("option", { name: "高" }));

    expect(screen.getByText("验证码回归用例")).toBeInTheDocument();
    expect(screen.queryByText("登录接口")).not.toBeInTheDocument();
    expect(screen.getByTestId("stat-TODO")).toHaveTextContent("1");
    expect(screen.getByTestId("count-TODO")).toHaveTextContent("1");
    expect(screen.getByTestId("stat-IN_PROGRESS")).toHaveTextContent("0");
    expect(screen.getByTestId("count-DONE")).toHaveTextContent("0");
  });

  it("仅 ADMIN 角色时按任务类型兜底，无需求的任务不生成链接", async () => {
    api.getCurrentUser.mockResolvedValue(currentUser(["ADMIN"], 9));
    render(<TaskBoard />, { wrapper });

    expect(await screen.findByRole("link", { name: "验证码回归用例" })).toHaveAttribute("href", "/requirements/2/qa");
    expect(screen.getByRole("link", { name: "登录流程设计稿" })).toHaveAttribute("href", "/requirements/1/ux");
    expect(screen.getByText("无关联需求的缺陷")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "无关联需求的缺陷" })).not.toBeInTheDocument();
  });

  it("同列拖拽调用 moveBoardItem，跨列拖拽仅提示", async () => {
    render(<TaskBoard />, { wrapper });
    await screen.findByText("登录接口");

    const sameLane = fakeDataTransfer();
    fireEvent.dragStart(screen.getByTestId("card-1"), { dataTransfer: sameLane });
    fireEvent.drop(screen.getByTestId("column-TODO"), { dataTransfer: sameLane });
    await waitFor(() => expect(api.moveBoardItem).toHaveBeenCalledWith(1, "TODO", 2000));

    const crossLane = fakeDataTransfer();
    fireEvent.dragStart(screen.getByTestId("card-1"), { dataTransfer: crossLane });
    fireEvent.drop(screen.getByTestId("column-DONE"), { dataTransfer: crossLane });
    expect(api.moveBoardItem).toHaveBeenCalledTimes(1);
    expect(Message.info).toHaveBeenCalledWith("请进入任务工作区执行状态操作。");
  });

  it("taskTarget 角色优先、类型兜底、无需求返回空", () => {
    const item = boardItem({ requirementId: 7, type: "DEV_TASK" });
    expect(taskTarget(["PRODUCT"], item)).toBe("/requirements/7");
    expect(taskTarget(["UX"], item)).toBe("/requirements/7/ux");
    expect(taskTarget(["DEVELOPER"], item)).toBe("/requirements/7/dev");
    expect(taskTarget(["QA"], item)).toBe("/requirements/7/qa");
    expect(taskTarget(["ADMIN"], { ...item, type: "QA_TASK" })).toBe("/requirements/7/qa");
    expect(taskTarget(["ADMIN"], item)).toBe("/requirements/7/dev");
    expect(taskTarget(undefined, item)).toBe("/requirements/7/dev");
    expect(taskTarget(["ADMIN"], { ...item, requirementId: null })).toBeNull();
  });
});