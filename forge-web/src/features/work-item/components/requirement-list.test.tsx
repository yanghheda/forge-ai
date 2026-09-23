import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Message } from "@arco-design/web-react";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useAgentPanelStore } from "@/stores/use-agent-panel-store";

import { RequirementList } from "./requirement-list";

const api = vi.hoisted(() => ({
  listOrganizationRequirements: vi.fn(),
  updateWorkItem: vi.fn(),
  deleteRequirement: vi.fn(),
}));

vi.mock("../api/work-item-api", async () => ({
  ...(await vi.importActual("../api/work-item-api")),
  ...api,
}));

function wrapper({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={["/requirements"]}>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

const draftRequirement = {
  id: 1,
  itemKey: "REQ-1",
  title: "登录页改版",
  description: "",
  status: "DRAFT",
  priority: "HIGH",
  dueAt: null,
  version: 0,
  createdAt: "2026-09-09",
  updatedAt: "2026-09-09",
};

describe("RequirementList", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(Message, "success").mockImplementation(() => () => undefined);
    vi.spyOn(Message, "error").mockImplementation(() => () => undefined);
    api.listOrganizationRequirements.mockResolvedValue({ items: [draftRequirement], page: 1, pageSize: 20, total: 1 });
    useAgentPanelStore.setState({ open: false, context: undefined, contextDismissed: false });
  });

  it("行信息区链接到需求详情", async () => {
    render(<RequirementList />, { wrapper });

    expect(await screen.findByRole("link", { name: /登录页改版/ })).toHaveAttribute("href", "/requirements/1");
  });

  it("行内「交给 Agent」写入需求上下文并展开指令抽屉", async () => {
    const user = userEvent.setup();
    render(<RequirementList />, { wrapper });
    await screen.findByText("登录页改版");

    await user.click(screen.getByRole("button", { name: "交给 Agent" }));

    expect(useAgentPanelStore.getState().context).toEqual({ kind: "requirement", id: 1, label: "REQ-1 登录页改版" });
    expect(useAgentPanelStore.getState().open).toBe(true);
  });

  it("修改弹窗提交时携带当前行版本与变更字段", async () => {
    const user = userEvent.setup();
    api.updateWorkItem.mockResolvedValue({ id: 1, version: 1 });
    render(<RequirementList />, { wrapper });
    await screen.findByText("登录页改版");

    await user.click(screen.getByRole("button", { name: "修改" }));
    await user.click(screen.getByRole("combobox", { name: "优先级" }));
    // 下拉弹层在 jsdom 中停留在进入动画态（pointer-events: none），因此直接派发点击事件。
    fireEvent.click(await screen.findByRole("option", { name: "低优先级" }));
    await user.click(screen.getByRole("button", { name: "保存" }));

    await waitFor(() => expect(api.updateWorkItem).toHaveBeenCalledWith(1, { priority: "LOW", expectedVersion: 0 }));
  });

  it("删除草稿需求需二次确认并携带版本", async () => {
    const user = userEvent.setup();
    api.deleteRequirement.mockResolvedValue(undefined);
    render(<RequirementList />, { wrapper });
    await screen.findByText("登录页改版");

    await user.click(screen.getByRole("button", { name: "删除" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/不再出现在需求列表中/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "确认删除" }));

    await waitFor(() => expect(api.deleteRequirement).toHaveBeenCalledWith(1, 0));
  });

  it("非草稿需求不渲染删除入口", async () => {
    api.listOrganizationRequirements.mockResolvedValue({ items: [{ ...draftRequirement, status: "IN_DEVELOPMENT" }], page: 1, pageSize: 20, total: 1 });
    render(<RequirementList />, { wrapper });
    await screen.findByText("登录页改版");

    expect(screen.queryByRole("button", { name: "删除" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "修改" })).toBeInTheDocument();
  });
});
