import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ProjectAccessGuard } from "./project-access-guard";

const replace = vi.fn();
const authApi = vi.hoisted(() => ({ getCurrentUser: vi.fn() }));
const projectApi = vi.hoisted(() => ({ listProjects: vi.fn() }));

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));
vi.mock("@/features/auth", () => authApi);
vi.mock("../api/project-api", () => projectApi);

function wrapper({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      {children}
    </QueryClientProvider>
  );
}

describe("ProjectAccessGuard", () => {
  beforeEach(() => vi.clearAllMocks());

  it("目标项目不在当前成员可访问列表时跳回首页且不渲染项目页面", async () => {
    authApi.getCurrentUser.mockResolvedValue({
      id: 1,
      workspaces: [{ id: 10, slug: "engineering", name: "Engineering", roles: [] }],
    });
    projectApi.listProjects.mockResolvedValue([
      { id: 20, key: "OTHER", name: "Other project" },
    ]);

    render(
      <ProjectAccessGuard workspaceSlug="engineering" projectKey="SECRET">
        <div>项目私有内容</div>
      </ProjectAccessGuard>,
      { wrapper },
    );

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/w/engineering"));
    expect(screen.queryByText("项目私有内容")).not.toBeInTheDocument();
  });

  it("项目成员可以进入目标项目页面", async () => {
    authApi.getCurrentUser.mockResolvedValue({
      id: 1,
      workspaces: [{ id: 10, slug: "engineering", name: "Engineering", roles: [] }],
    });
    projectApi.listProjects.mockResolvedValue([
      { id: 20, key: "FORGE", name: "ForgeAI" },
    ]);

    render(
      <ProjectAccessGuard workspaceSlug="engineering" projectKey="FORGE">
        <div>项目私有内容</div>
      </ProjectAccessGuard>,
      { wrapper },
    );

    expect(await screen.findByText("项目私有内容")).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });
});
