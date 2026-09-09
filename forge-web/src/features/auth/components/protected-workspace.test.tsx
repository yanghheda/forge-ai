import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api";
import { ProtectedWorkspace } from "./protected-workspace";

const replace = vi.fn();
const authApi = vi.hoisted(() => ({ getCurrentUser: vi.fn(), logout: vi.fn() }));

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));
vi.mock("../api/auth-api", async () => ({ ...await vi.importActual("../api/auth-api"), ...authApi }));

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;
}

describe("ProtectedWorkspace", () => {
  beforeEach(() => vi.clearAllMocks());
  afterEach(cleanup);

  it("仅为 me 返回的 Workspace 展示受保护内容并可退出", async () => {
    authApi.getCurrentUser.mockResolvedValue({ id: 1, email: "owner@example.com", displayName: "Owner", workspaces: [
      { id: 2, slug: "engineering", name: "Engineering", roles: ["OWNER"] },
    ] });
    authApi.logout.mockResolvedValue(undefined);
    render(<ProtectedWorkspace workspace="engineering"><div>受保护内容</div></ProtectedWorkspace>, { wrapper });

    expect(await screen.findByText("受保护内容")).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "退出" }));
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
  });

  it("401 与无 Workspace 权限分别处理", async () => {
    authApi.getCurrentUser.mockRejectedValue(new ApiError({ code: "UNAUTHENTICATED", message: "Authentication required", status: 401 }));
    const first = render(<ProtectedWorkspace workspace="engineering"><div>不可见</div></ProtectedWorkspace>, { wrapper });
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
    first.unmount();

    authApi.getCurrentUser.mockResolvedValue({ id: 1, email: "owner@example.com", displayName: "Owner", workspaces: [] });
    render(<ProtectedWorkspace workspace="engineering"><div>不可见</div></ProtectedWorkspace>, { wrapper });
    expect(await screen.findByText("当前账户无权访问此工作空间。")).toBeInTheDocument();
    expect(screen.queryByText("不可见")).not.toBeInTheDocument();
  });
});
