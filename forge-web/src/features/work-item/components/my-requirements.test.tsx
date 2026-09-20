import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { MyRequirements } from "./my-requirements";

const api = vi.hoisted(() => ({
  listOrganizationRequirements: vi.fn(),
  createOrganizationRequirement: vi.fn(),
}));

vi.mock("../api/work-item-api", async () => ({
  ...(await vi.importActual("../api/work-item-api")),
  ...api,
}));

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;
}

describe("MyRequirements", () => {
  afterEach(cleanup);

  beforeEach(() => {
    api.listOrganizationRequirements.mockResolvedValue({
      items: [
        {
          id: 1,
          itemKey: "REQ-1",
          title: "登录页改版",
          description: "",
          status: "DRAFT",
          priority: "HIGH",
          version: 0,
          createdAt: "2026-09-09",
          updatedAt: "2026-09-09",
        },
      ],
      page: 1,
      pageSize: 20,
      total: 1,
    });
  });

  it("只查询与我相关的需求并渲染列表", async () => {
    render(<MyRequirements />, { wrapper });

    expect(screen.getByText("我创建、我负责或等待我评审的需求")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("搜索需求标题、编号或描述")).toBeInTheDocument();
    expect(await screen.findByText("登录页改版")).toBeInTheDocument();
    expect(screen.getByText("共 1 条需求")).toBeInTheDocument();
    expect(api.listOrganizationRequirements).toHaveBeenCalledWith({ mine: true, q: "", status: undefined });
  });

  it("点击新建需求后在抽屉中展示创建表单", async () => {
    render(<MyRequirements />, { wrapper });
    const user = userEvent.setup();

    expect(screen.queryByLabelText("需求标题")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "新建需求" }));

    expect(screen.getByLabelText("需求标题")).toBeInTheDocument();
    expect(screen.getByLabelText("需求描述")).toBeInTheDocument();
    expect(screen.getByLabelText("需求优先级")).toBeInTheDocument();
    expect(screen.getByLabelText("新建需求表单")).toHaveClass("arco-form");
    expect(document.querySelector(".arco-drawer-header-title")).toHaveTextContent("创建需求");
    expect(screen.getByText("记录问题背景与优先级，创建后可继续完善协作人员和交付材料。").closest(".arco-drawer-content")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "创建需求" })).toBeDisabled();
  });
});
