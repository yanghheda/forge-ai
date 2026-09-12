import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Message } from "@arco-design/web-react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

const { listCompanyMembers, updateCompanyMember } = vi.hoisted(() => ({
  listCompanyMembers: vi.fn(),
  updateCompanyMember: vi.fn(),
}));

vi.mock("@/features/auth", () => ({
  ProtectedApp: ({ children }: { children: ReactNode }) => children,
  getCurrentUser: vi.fn().mockResolvedValue({
    id: 1,
    email: "owner@uat.forgeai.local",
    displayName: "Forge Owner",
    organization: { id: 1, slug: "forge", name: "Forge", roles: ["OWNER"] },
  }),
}));

vi.mock("@/features/console", () => ({
  listCompanyMembers,
  updateCompanyMember,
}));

import MembersPage from "./page";

const messageSuccess = vi.spyOn(Message, "success").mockImplementation(() => undefined as never);

const pendingMember = {
  userId: 7,
  displayName: "张三",
  email: "product@uat.forgeai.local",
  status: "PENDING" as const,
  roles: ["PRODUCT"],
  lastLoginAt: null,
  version: 3,
};

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;
}

describe("MembersPage", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("使用 Modal 展示待审核成员信息和审核操作", async () => {
    listCompanyMembers.mockResolvedValue([pendingMember]);

    render(<MembersPage />, { wrapper });
    await userEvent.click(await screen.findByRole("button", { name: "审核" }));

    const dialog = screen.getByRole("dialog", { name: "审核成员" });
    expect(dialog).toHaveTextContent("张三");
    expect(dialog).toHaveTextContent("product@uat.forgeai.local");
    expect(dialog).toHaveTextContent("产品");
    expect(screen.getByRole("button", { name: "通过" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "拒绝" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "取消" })).toBeInTheDocument();
  });

  it.each([
    ["通过", "ACTIVE"],
    ["拒绝", "DISABLED"],
  ])("点击%s提交对应审核状态", async (action, status) => {
    listCompanyMembers.mockResolvedValue([pendingMember]);
    updateCompanyMember.mockResolvedValue({ ...pendingMember, status });

    render(<MembersPage />, { wrapper });
    await userEvent.click(await screen.findByRole("button", { name: "审核" }));
    await userEvent.click(screen.getByRole("button", { name: action }));

    await waitFor(() =>
      expect(updateCompanyMember).toHaveBeenCalledWith(7, {
        displayName: "张三",
        roles: ["PRODUCT"],
        status,
        expectedVersion: 3,
      }),
    );
  });

  it("编辑时允许修改姓名并分配多个角色（含管理员）", async () => {
    const member = {
      ...pendingMember,
      userId: 8,
      displayName: "李开发",
      email: "dev@uat.forgeai.local",
      status: "ACTIVE" as const,
      roles: ["DEVELOPER", "ADMIN"],
    };
    listCompanyMembers.mockResolvedValue([member]);
    updateCompanyMember.mockResolvedValue({ ...member, displayName: "李研发" });

    render(<MembersPage />, { wrapper });
    await userEvent.click(await screen.findByRole("button", { name: "编辑" }));

    const nameInput = screen.getByDisplayValue("李开发");
    expect(nameInput).not.toHaveAttribute("readonly");
    expect(screen.getByRole("combobox", { name: "岗位角色" })).toHaveTextContent("研发");
    expect(screen.getByRole("combobox", { name: "岗位角色" })).toHaveTextContent("管理员");

    await userEvent.clear(nameInput);
    await userEvent.type(nameInput, "李研发");
    await userEvent.click(screen.getByRole("button", { name: "保存修改" }));

    await waitFor(() =>
      expect(updateCompanyMember).toHaveBeenCalledWith(8, {
        displayName: "李研发",
        roles: ["DEVELOPER", "ADMIN"],
        status: "ACTIVE",
        expectedVersion: 3,
      }),
    );
  });

  it("不允许编辑当前登录账号", async () => {
    const owner = {
      ...pendingMember,
      userId: 1,
      status: "ACTIVE" as const,
      roles: ["OWNER"],
    };
    listCompanyMembers.mockResolvedValue([owner]);

    render(<MembersPage />, { wrapper });

    expect(await screen.findByRole("button", { name: "编辑" })).toBeDisabled();
  });

  it("审核通过成功后显示全局成功提示", async () => {
    listCompanyMembers.mockResolvedValue([pendingMember]);
    updateCompanyMember.mockResolvedValue({ ...pendingMember, status: "ACTIVE" });

    render(<MembersPage />, { wrapper });
    await userEvent.click(await screen.findByRole("button", { name: "审核" }));
    await userEvent.click(screen.getByRole("button", { name: "通过" }));

    await waitFor(() => expect(messageSuccess).toHaveBeenCalledWith("审核成功"));
  });
});
