import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { StageTasks } from "./stage-tasks";

const api = vi.hoisted(() => ({
  getOrganizationRequirement: vi.fn(),
  getRequirementParticipants: vi.fn(),
  listRequirementMembers: vi.fn(),
  listRequirementTasks: vi.fn(),
  createStageTask: vi.fn(),
  transitionTask: vi.fn(),
  startDevelopment: vi.fn(),
  completeDevTask: vi.fn(),
  getCurrentUser: vi.fn(),
}));
vi.mock("../api/work-item-api", () => api);
vi.mock("@/features/auth", () => ({ getCurrentUser: api.getCurrentUser }));
const wrapper = ({ children }: { children: React.ReactNode }) => <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;
afterEach(cleanup);
beforeEach(() => {
  vi.clearAllMocks();
  api.getCurrentUser.mockResolvedValue({ id: 2 });
  api.getOrganizationRequirement.mockResolvedValue({ status: "READY_FOR_UX" });
  api.getRequirementParticipants.mockResolvedValue([{ role: "UX", userId: 2, executorUserIds: [5] }]);
  api.listRequirementMembers.mockResolvedValue([
    { userId: 5, displayName: "设计执行人" },
    { userId: 6, displayName: "未关联成员" },
  ]);
  api.listRequirementTasks.mockResolvedValue([]);
  api.createStageTask.mockResolvedValue({ id: 10 });
  api.transitionTask.mockResolvedValue({});
  api.startDevelopment.mockResolvedValue({});
  api.completeDevTask.mockResolvedValue({});
});

it("负责人必须选择已关联执行人后才能创建设计任务", async () => {
  render(<StageTasks requirementId={1} type="UX_TASK" />, { wrapper });
  const create = await screen.findByRole("button", { name: "创建设计任务" });
  fireEvent.change(screen.getByLabelText("设计任务标题"), { target: { value: "登录页设计" } });
  expect(create).toBeDisabled();
  fireEvent.click(screen.getAllByText("选择已关联执行人")[0]);
  fireEvent.click(await screen.findByText("设计执行人"));
  expect(screen.queryByText("未关联成员")).not.toBeInTheDocument();
  fireEvent.click(create);
  await waitFor(() => expect(api.createStageTask).toHaveBeenCalledWith(1, { type: "UX_TASK", title: "登录页设计", assigneeUserId: 5 }));
});

it("普通执行人及非准备阶段都没有创建入口", async () => {
  api.getCurrentUser.mockResolvedValue({ id: 5 });
  render(<StageTasks requirementId={1} type="UX_TASK" />, { wrapper });
  await screen.findByText(/尚未创建设计任务/);
  expect(screen.queryByRole("button", { name: "创建设计任务" })).not.toBeInTheDocument();
  cleanup();
  api.getCurrentUser.mockResolvedValue({ id: 2 });
  api.getOrganizationRequirement.mockResolvedValue({ status: "UX_IN_PROGRESS" });
  render(<StageTasks requirementId={1} type="UX_TASK" />, { wrapper });
  await screen.findByText(/尚未创建设计任务/);
  expect(screen.queryByRole("button", { name: "创建设计任务" })).not.toBeInTheDocument();
});

it("待验证任务按服务端动作展示审核，并携带版本和驳回原因", async () => {
  api.listRequirementTasks.mockResolvedValue([{ id: 10, itemKey: "REQ-10", type: "UX_TASK", title: "设计", status: "WAITING_VERIFICATION", assigneeUserId: 5, version: 3, availableActions: ["APPROVE", "REJECT"], guardHints: { REJECT: ["reason"] } }]);
  render(<StageTasks requirementId={1} type="UX_TASK" />, { wrapper });
  expect(await screen.findByText("待验证")).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "提交验证" })).not.toBeInTheDocument();
  const reject = screen.getByRole("button", { name: "退回修改" });
  expect(reject).toBeDisabled();
  fireEvent.change(screen.getByLabelText("任务操作原因"), { target: { value: "补充异常态" } });
  fireEvent.click(reject);
  await waitFor(() => expect(api.transitionTask).toHaveBeenCalledWith(10, "REJECT", 3, "补充异常态"));
});

it("开发任务的开始处理走启动开发，以创建源分支与合并请求", async () => {
  api.listRequirementTasks.mockResolvedValue([{ id: 20, itemKey: "DEV-20", type: "DEV_TASK", title: "前端开发", status: "TODO", assigneeUserId: 5, version: 1, availableActions: ["START"], guardHints: {} }]);
  render(<StageTasks requirementId={1} type="DEV_TASK" />, { wrapper });
  fireEvent.click(await screen.findByRole("button", { name: "开始处理" }));
  await waitFor(() => expect(api.startDevelopment).toHaveBeenCalledWith({ organizationId: 0, taskId: 20, idempotencyKey: expect.any(String) }));
  expect(api.transitionTask).not.toHaveBeenCalled();
});

it("开发任务的提交验证走完成开发任务，以校验 MR 已合并", async () => {
  api.listRequirementTasks.mockResolvedValue([{ id: 21, itemKey: "DEV-21", type: "DEV_TASK", title: "接口开发", status: "IN_PROGRESS", assigneeUserId: 5, version: 4, availableActions: ["SUBMIT_REVIEW", "BLOCK"], guardHints: {} }]);
  render(<StageTasks requirementId={1} type="DEV_TASK" />, { wrapper });
  fireEvent.click(await screen.findByRole("button", { name: "提交验证" }));
  await waitFor(() => expect(api.completeDevTask).toHaveBeenCalledWith({ organizationId: 0, taskId: 21, expectedVersion: 4 }));
  expect(api.transitionTask).not.toHaveBeenCalled();
});

it("非开发任务仍按服务端动作走通用流转", async () => {
  api.listRequirementTasks.mockResolvedValue([{ id: 22, itemKey: "QA-22", type: "QA_TASK", title: "回归", status: "IN_PROGRESS", assigneeUserId: 5, version: 2, availableActions: ["SUBMIT_REVIEW"], guardHints: {} }]);
  render(<StageTasks requirementId={1} type="QA_TASK" />, { wrapper });
  fireEvent.click(await screen.findByRole("button", { name: "提交验证" }));
  await waitFor(() => expect(api.transitionTask).toHaveBeenCalledWith(22, "SUBMIT_REVIEW", 2, ""));
  expect(api.completeDevTask).not.toHaveBeenCalled();
});
