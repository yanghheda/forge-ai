import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api";
import type { BugView } from "../api/work-item-api";

const api = vi.hoisted(() => ({
  getBug: vi.fn(),
  transitionBug: vi.fn(),
}));

vi.mock("../api/work-item-api", () => ({ getBug: api.getBug, transitionBug: api.transitionBug }));

import { DefectDetail } from "./defect-detail";

const bug = (overrides: Partial<BugView>): BugView => ({
  id: 9,
  itemKey: "BUG-9",
  title: "登录接口偶发 500",
  status: "WAITING_VERIFICATION",
  severity: "CRITICAL",
  requirementId: 7,
  testRunId: 3,
  testResultId: 4,
  devTaskId: 8,
  devTaskKey: "DEV-8",
  assigneeUserId: 2,
  assigneeName: "王开发",
  reporterUserId: 9,
  reporterName: "赵测试",
  reproductionSteps: ["打开登录页", "输入错误密码", "点击登录"],
  expectedResult: "提示密码错误",
  actualResult: "页面返回 500",
  fixNote: "修复空指针判断",
  fixEvidence: ["mr://42", "https://ci.example.com/1"],
  version: 3,
  createdAt: "2026-09-01T08:00:00Z",
  updatedAt: "2026-09-02T09:00:00Z",
  ...overrides,
});

function wrapper({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={["/defects/9"]}>
        <Routes>
          <Route path="/defects/:id" element={children} />
          <Route path="/defects" element={<div>缺陷列表占位</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("DefectDetail", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    api.getBug.mockResolvedValue(bug({}));
  });

  it("渲染复现步骤、预期结果、实际结果、修复说明与修复证据", async () => {
    render(<DefectDetail />, { wrapper });

    expect(await screen.findByRole("heading", { name: "登录接口偶发 500" })).toBeInTheDocument();
    expect(screen.getByText("打开登录页")).toBeInTheDocument();
    expect(screen.getByText("输入错误密码")).toBeInTheDocument();
    expect(screen.getByText("点击登录")).toBeInTheDocument();
    expect(screen.getByText("提示密码错误")).toBeInTheDocument();
    expect(screen.getByText("页面返回 500")).toBeInTheDocument();
    expect(screen.getByText("修复空指针判断")).toBeInTheDocument();
    expect(screen.getByText("mr://42")).toBeInTheDocument();
    expect(screen.getByText("https://ci.example.com/1")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "缺陷内容" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "修复信息" })).toBeInTheDocument();
    expect(api.getBug).toHaveBeenCalledWith(9);
  });

  it("展示创建人与修复人及元信息", async () => {
    render(<DefectDetail />, { wrapper });

    await screen.findByRole("heading", { name: "登录接口偶发 500" });
    expect(screen.getByText("修复人")).toBeInTheDocument();
    expect(screen.getByText("王开发")).toBeInTheDocument();
    expect(screen.getByText("创建人")).toBeInTheDocument();
    expect(screen.getByText("赵测试")).toBeInTheDocument();
    expect(screen.getByText("关联开发任务")).toBeInTheDocument();
    expect(screen.getByText("DEV-8")).toBeInTheDocument();
  });

  it("返回链接指向缺陷列表", async () => {
    render(<DefectDetail />, { wrapper });

    await screen.findByRole("heading", { name: "登录接口偶发 500" });
    expect(screen.getByRole("link", { name: /返回缺陷列表/ })).toHaveAttribute("href", "/defects");
  });

  it("关联需求链接指向对应需求", async () => {
    render(<DefectDetail />, { wrapper });

    await screen.findByRole("heading", { name: "登录接口偶发 500" });
    expect(screen.getByRole("link", { name: "REQ-7" })).toHaveAttribute("href", "/requirements/7");
  });

  it("只读视图不出现状态操作按钮且不调用状态流转", async () => {
    render(<DefectDetail />, { wrapper });

    await screen.findByRole("heading", { name: "登录接口偶发 500" });
    expect(screen.queryByRole("button", { name: "开始修复" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "提交验证" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "验证通过" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "开始验证" })).not.toBeInTheDocument();
    expect(screen.queryAllByRole("button")).toHaveLength(0);
    expect(api.transitionBug).not.toHaveBeenCalled();
  });

  it("接口 404 时展示错误提示", async () => {
    api.getBug.mockRejectedValue(new ApiError({ code: "RESOURCE_NOT_FOUND", message: "RESOURCE_NOT_FOUND", status: 404 }));
    render(<DefectDetail />, { wrapper });

    expect(await screen.findByText("请求的资源不存在或当前账户无权访问。")).toBeInTheDocument();
  });
});
