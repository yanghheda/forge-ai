import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api";

import { AuthEntry } from "./auth-entry";

const replace = vi.fn();
const authApi = vi.hoisted(() => ({
  getSetupStatus: vi.fn(),
  initializeInstance: vi.fn(),
  login: vi.fn(),
  register: vi.fn(),
  getCurrentUser: vi.fn(),
}));

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));
vi.mock("../api/auth-api", async () => ({
  ...(await vi.importActual("../api/auth-api")),
  ...authApi,
}));

function wrapper({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
        })
      }
    >
      {children}
    </QueryClientProvider>
  );
}

describe("AuthEntry", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(cleanup);

  it("未初始化实例展示完整初始化表单", async () => {
    authApi.getSetupStatus.mockResolvedValue({ initialized: false });
    const { container } = render(<AuthEntry />, { wrapper });

    expect(await screen.findByText("初始化 ForgeAI")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "让需求到发布 由 AI 智能体协同完成" })).toBeInTheDocument();
    expect(screen.queryByText(/\\n/)).not.toBeInTheDocument();
    expect(screen.getByLabelText("管理员邮箱")).toBeInTheDocument();
    expect(screen.getByLabelText("公司名称")).toBeInTheDocument();
    expect(document.querySelectorAll(".arco-form-item-symbol")).toHaveLength(6);
    expect(screen.getByLabelText("管理员邮箱").closest("form")).toHaveClass("arco-form");
    expect(container.querySelector('input[type="file"]')).toHaveAttribute("accept", "image/webp,.webp");
    expect(screen.getByLabelText("点击或拖拽文件到此处上传")).toBeInTheDocument();
    expect(screen.getByText("仅支持 WebP 格式，比例 1:1，文件不超过 2 MiB")).toBeInTheDocument();
    expect(screen.queryByLabelText("工作空间名称")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "完成初始化" })).toBeEnabled();
  });

  it("初始化路由始终展示初始化向导", async () => {
    authApi.getSetupStatus.mockResolvedValue({ initialized: true });
    render(<AuthEntry initialMode="init" />, { wrapper });

    expect(await screen.findByText("初始化 ForgeAI")).toBeInTheDocument();
    expect(screen.getByLabelText("点击或拖拽文件到此处上传")).toBeInTheDocument();
  });

  it("点击 Logo 上传区域会打开文件选择器", async () => {
    authApi.getSetupStatus.mockResolvedValue({ initialized: false });
    const { container } = render(<AuthEntry />, { wrapper });
    const user = userEvent.setup();
    await screen.findByText("初始化 ForgeAI");
    const fileInput = container.querySelector<HTMLInputElement>('input[type="file"]');
    expect(fileInput).not.toBeNull();
    const openFilePicker = vi.spyOn(fileInput!, "click");

    await user.click(screen.getByLabelText("点击或拖拽文件到此处上传"));

    expect(openFilePicker).toHaveBeenCalledOnce();
  });

  it("选择 Logo 后在上传方块内显示图片和重新上传入口", async () => {
    authApi.getSetupStatus.mockResolvedValue({ initialized: false });
    const { container } = render(<AuthEntry />, { wrapper });
    const user = userEvent.setup();
    const logo = new File(["RIFF0000WEBP"], "company.webp", {
      type: "image/webp",
    });

    await screen.findByText("初始化 ForgeAI");
    const fileInput = container.querySelector<HTMLInputElement>('input[type="file"]');
    expect(fileInput).not.toBeNull();
    await user.upload(fileInput!, logo);

    expect(await screen.findByRole("img", { name: "company.webp Logo" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重新上传公司 Logo" })).toBeInTheDocument();
    expect(screen.getByText("重新上传")).toBeInTheDocument();
    expect(screen.queryByText("已选择，将在完成初始化时上传")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "预览图片" })).not.toBeInTheDocument();
    expect(container.querySelector(".arco-upload-list-start-icon")).not.toBeInTheDocument();
  });

  it("初始化校验使用中文字段提示并包含 Logo", async () => {
    authApi.getSetupStatus.mockResolvedValue({ initialized: false });
    render(<AuthEntry />, { wrapper });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "完成初始化" }));

    expect(await screen.findByText("请输入管理员名称")).toBeInTheDocument();
    expect(screen.getByText("请输入密码")).toBeInTheDocument();
    expect(screen.getByText("请输入组织名称")).toBeInTheDocument();
    expect(screen.getByText("请输入公司短名")).toBeInTheDocument();
    expect(screen.getByText("请选择公司 Logo")).toBeInTheDocument();
    expect(screen.queryByText(/Invalid input/)).not.toBeInTheDocument();
  });

  it("登录成功后直接进入需求概览", async () => {
    authApi.getSetupStatus.mockResolvedValue({ initialized: true });
    authApi.login.mockResolvedValue(undefined);
    authApi.getCurrentUser.mockResolvedValue({
      id: 1,
      email: "owner@example.com",
      displayName: "Owner",
      organizations: [{ id: 2, slug: "engineering", name: "Engineering", roles: ["OWNER"] }],
    });
    render(<AuthEntry />, { wrapper });
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("邮箱"), "owner@example.com");
    await user.type(screen.getByLabelText("密码"), "correct-horse-42");
    await user.click(screen.getByRole("button", { name: "登录" }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/overview"));
    await waitFor(() => {
      expect(screen.getByLabelText("邮箱")).toHaveValue("");
      expect(screen.getByLabelText("密码")).toHaveValue("");
    });
  });

  it("初始化后提供团队成员自助注册入口", async () => {
    authApi.getSetupStatus.mockResolvedValue({ initialized: true });
    authApi.register.mockResolvedValue({ userId: 2 });
    render(<AuthEntry />, { wrapper });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "注册账号" }));
    expect(screen.getByLabelText("岗位角色")).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "所有者" })).not.toBeInTheDocument();
  });

  it("登录与注册切换按钮使用无背景的纯文字样式", async () => {
    authApi.getSetupStatus.mockResolvedValue({ initialized: true });
    render(<AuthEntry />, { wrapper });
    const user = userEvent.setup();

    const registerButton = await screen.findByRole("button", { name: "注册账号" });
    expect(registerButton.className).toContain("authSwitchButton");
    expect(registerButton).not.toHaveClass("arco-btn-long");

    await user.click(registerButton);
    const loginButton = screen.getByRole("button", { name: "已有账号，返回登录" });
    expect(loginButton.className).toContain("authSwitchButton");
    expect(loginButton).not.toHaveClass("arco-btn-long");
  });

  it("注册成功展示提示并返回登录", async () => {
    authApi.getSetupStatus.mockResolvedValue({ initialized: true });
    authApi.register.mockResolvedValue({ userId: 2 });
    render(<AuthEntry />, { wrapper });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "注册账号" }));
    await user.type(screen.getByLabelText("姓名"), "Developer");
    await user.type(screen.getByLabelText("邮箱"), "developer@example.com");
    await user.type(screen.getByLabelText("密码"), "correct-horse-42");
    await user.click(screen.getByRole("button", { name: "完成注册" }));

    expect(await screen.findByText("注册申请已提交，请等待公司管理员审核后登录。")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "登录" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "注册账号" }));
    expect(screen.getByLabelText("姓名")).toHaveValue("");
    expect(screen.getByLabelText("邮箱")).toHaveValue("");
    expect(screen.getByLabelText("密码")).toHaveValue("");
    expect(screen.getByLabelText("岗位角色")).toHaveTextContent("产品");
  });

  it("表单校验失败展示提示", async () => {
    authApi.getSetupStatus.mockResolvedValue({ initialized: true });
    render(<AuthEntry />, { wrapper });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "登录" }));

    const error = await screen.findByText("请输入有效邮箱");
    expect(error.closest(".arco-form-item")).toContainElement(screen.getByRole("textbox"));
    expect(screen.getByRole("textbox").closest("form")).toHaveAttribute("novalidate");
  });

  it("账号不存在或密码错误时展示中文提示且不显示请求编号", async () => {
    authApi.getSetupStatus.mockResolvedValue({ initialized: true });
    authApi.login.mockRejectedValue(
      new ApiError({
        code: "UNAUTHENTICATED",
        message: "Invalid email or password",
        requestId: "request-123",
        status: 401,
      }),
    );
    render(<AuthEntry />, { wrapper });
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("邮箱"), "missing@example.com");
    await user.type(screen.getByLabelText("密码"), "incorrect-password-42");
    await user.click(screen.getByRole("button", { name: "登录" }));

    expect(await screen.findByText("账号不存在或密码错误，请重新输入。")).toBeInTheDocument();
    expect(screen.getByLabelText("邮箱")).toHaveValue("missing@example.com");
    expect(screen.getByLabelText("密码")).toHaveValue("incorrect-password-42");
    expect(screen.queryByText(/请求编号/)).not.toBeInTheDocument();
    expect(screen.queryByText(/request-123/)).not.toBeInTheDocument();
  });

  it("注册失败时在注册表单内展示错误提示", async () => {
    authApi.getSetupStatus.mockResolvedValue({ initialized: true });
    authApi.register.mockRejectedValue(
      new ApiError({
        code: "CONFLICT",
        message: "Email already exists",
        status: 409,
      }),
    );
    render(<AuthEntry />, { wrapper });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "注册账号" }));
    await user.type(screen.getByLabelText("姓名"), "Developer");
    await user.type(screen.getByLabelText("邮箱"), "developer@example.com");
    await user.type(screen.getByLabelText("密码"), "correct-horse-42");
    await user.click(screen.getByRole("button", { name: "完成注册" }));

    expect(await screen.findByText("数据状态发生冲突，请刷新后重试。")).toBeInTheDocument();
    expect(screen.getByLabelText("姓名")).toHaveValue("Developer");
    expect(screen.getByLabelText("邮箱")).toHaveValue("developer@example.com");
    expect(screen.getByLabelText("密码")).toHaveValue("correct-horse-42");
    expect(screen.getByRole("button", { name: "完成注册" })).toBeInTheDocument();
  });
});
