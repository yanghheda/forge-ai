// React 19 下 Arco 静态方法（Modal.confirm 等）需要显式注入 createRoot；
// 生产入口 app-providers.tsx 已引入该适配器，测试环境在此补齐以避免静态挂载失败。
import "@arco-design/web-react/lib/_util/react-19-adapter";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
  listPendingApprovals: vi.fn(),
  decideApproval: vi.fn(),
}));
const authApi = vi.hoisted(() => ({ getCurrentUser: vi.fn() }));

vi.mock("@/features/agent-run/api/agent-run-api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/features/agent-run/api/agent-run-api")>();
  return { ...actual, listPendingApprovals: api.listPendingApprovals, decideApproval: api.decideApproval };
});
vi.mock("@/features/auth", () => authApi);

import type { PendingApproval } from "@/features/agent-run";
import { ApiError } from "@/lib/api";
import { PendingApprovals } from "./pending-approvals";

function pendingApproval(overrides: Partial<PendingApproval> = {}): PendingApproval {
  return {
    id: "ap-1",
    toolName: "deploy_release",
    riskLevel: "HIGH",
    requestedBy: 9,
    requestedByName: "林敏",
    reason: "部署到生产环境",
    expiresAt: "2999-01-01T00:00:00Z",
    version: 1,
    ...overrides,
  };
}

function renderPendingApprovals() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  return render(<PendingApprovals />, { wrapper });
}

describe("PendingApprovals", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authApi.getCurrentUser.mockResolvedValue({
      id: 1,
      email: "approver@example.com",
      displayName: "审批人",
      organization: { id: 2, slug: "forge", name: "Forge", roles: ["OWNER"] },
    });
    api.decideApproval.mockResolvedValue({});
  });
  afterEach(cleanup);

  it("渲染待审批项的工具名、发起人与原因", async () => {
    api.listPendingApprovals.mockResolvedValue([pendingApproval()]);

    renderPendingApprovals();

    expect(await screen.findByText("deploy_release")).toBeInTheDocument();
    expect(screen.getByText("林敏")).toBeInTheDocument();
    expect(screen.getByText("部署到生产环境")).toBeInTheDocument();
  });

  it("点击批准并在确认后调用 decideApproval", async () => {
    const user = userEvent.setup();
    api.listPendingApprovals.mockResolvedValue([pendingApproval({ id: "ap-7", version: 5 })]);

    renderPendingApprovals();

    await user.click(await screen.findByRole("button", { name: "批准" }));
    await user.click(await screen.findByText("确定"));

    await waitFor(() => expect(api.decideApproval).toHaveBeenCalledWith({ id: "ap-7", version: 5 }, 2, "APPROVE"));
  });

  it("空列表显示空态", async () => {
    api.listPendingApprovals.mockResolvedValue([]);

    renderPendingApprovals();

    expect(await screen.findByText("暂无待审批")).toBeInTheDocument();
  });

  it("加载失败显示错误提示", async () => {
    api.listPendingApprovals.mockRejectedValue(new ApiError({ code: "FORBIDDEN", message: "FORBIDDEN", status: 403 }));

    renderPendingApprovals();

    expect(await screen.findByText("当前账户没有执行此操作的权限。")).toBeInTheDocument();
  });
});
