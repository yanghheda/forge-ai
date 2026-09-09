import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api";

import { AuthEntry } from "./auth-entry";

const replace = vi.fn();
const authApi = vi.hoisted(() => ({
  getSetupStatus: vi.fn(), initializeInstance: vi.fn(), login: vi.fn(), getCurrentUser: vi.fn(),
}));

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));
vi.mock("../api/auth-api", async () => ({ ...await vi.importActual("../api/auth-api"), ...authApi }));

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>{children}</QueryClientProvider>;
}

describe("AuthEntry", () => {
  beforeEach(() => { vi.clearAllMocks(); });
  afterEach(cleanup);

  it("未初始化实例展示完整初始化表单", async () => {
    authApi.getSetupStatus.mockResolvedValue({ initialized: false });
    render(<AuthEntry />, { wrapper });

    expect(await screen.findByText("初始化 ForgeAI")).toBeInTheDocument();
    expect(screen.getByLabelText("管理员邮箱")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "完成初始化" })).toBeEnabled();
  });

  it("登录成功后依据服务端 me 返回的 Workspace 跳转", async () => {
    authApi.getSetupStatus.mockResolvedValue({ initialized: true });
    authApi.login.mockResolvedValue(undefined);
    authApi.getCurrentUser.mockResolvedValue({
      id: 1, email: "owner@example.com", displayName: "Owner",
      workspaces: [{ id: 2, slug: "engineering", name: "Engineering", roles: ["OWNER"] }],
    });
    render(<AuthEntry />, { wrapper });
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("邮箱"), "owner@example.com");
    await user.type(screen.getByLabelText("密码"), "correct-horse-42");
    await user.click(screen.getByRole("button", { name: "登录" }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/w/engineering"));
  });

  it("账号不存在或密码错误时展示中文提示且不显示请求编号", async () => {
    authApi.getSetupStatus.mockResolvedValue({ initialized: true });
    authApi.login.mockRejectedValue(new ApiError({
      code: "UNAUTHENTICATED",
      message: "Invalid email or password",
      requestId: "request-123",
      status: 401,
    }));
    render(<AuthEntry />, { wrapper });
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("邮箱"), "missing@example.com");
    await user.type(screen.getByLabelText("密码"), "incorrect-password-42");
    await user.click(screen.getByRole("button", { name: "登录" }));

    expect(await screen.findByText("账号不存在或密码错误，请重新输入。")).toBeInTheDocument();
    expect(screen.queryByText(/请求编号/)).not.toBeInTheDocument();
    expect(screen.queryByText(/request-123/)).not.toBeInTheDocument();
  });
});
