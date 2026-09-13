import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Message } from "@arco-design/web-react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

const { bindRepository, configureWebhookSecret, createConnection, listConnections, testConnection } = vi.hoisted(() => ({
  bindRepository: vi.fn(),
  configureWebhookSecret: vi.fn(),
  createConnection: vi.fn(),
  listConnections: vi.fn(),
  testConnection: vi.fn(),
}));

vi.mock("@/features/auth", () => ({
  ProtectedApp: ({ children }: { children: ReactNode }) => children,
}));

vi.mock("@/features/gitlab", () => ({
  bindRepository,
  configureWebhookSecret,
  createConnection,
  listConnections,
  testConnection,
}));

import IntegrationsPage from "./page";

vi.spyOn(Message, "success").mockImplementation(() => undefined as never);

const connection = {
  id: 7,
  organizationId: 1,
  name: "Local GitLab",
  baseUrl: "http://localhost:8929",
  tokenFingerprint: "token-fp",
  status: "UNVERIFIED" as const,
  version: 0,
};

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;
}

describe("IntegrationsPage", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("可测试已有 GitLab 连接并绑定远端仓库", async () => {
    listConnections.mockResolvedValue([connection]);
    testConnection.mockResolvedValue({ externalUserId: "1", username: "root" });
    bindRepository.mockResolvedValue({ remoteProjectId: "root/forge-ai-demo" });

    render(<IntegrationsPage />, { wrapper });
    await userEvent.click(await screen.findByRole("button", { name: "配置" }));
    await userEvent.click(screen.getByRole("button", { name: "测试连接" }));

    await waitFor(() => expect(testConnection).toHaveBeenCalledWith(7));

    await userEvent.type(screen.getByLabelText("GitLab Project ID 或完整路径"), "root/forge-ai-demo");
    await userEvent.click(screen.getByRole("button", { name: "读取并绑定仓库" }));

    await waitFor(() =>
      expect(bindRepository).toHaveBeenCalledWith({
        connectionId: 7,
        remoteProjectId: "root/forge-ai-demo",
      }),
    );
  });

  it("为连接保存 Webhook Secret 且不回显保存值", async () => {
    listConnections.mockResolvedValue([connection]);
    configureWebhookSecret.mockResolvedValue(undefined);

    render(<IntegrationsPage />, { wrapper });
    await userEvent.click(await screen.findByRole("button", { name: "配置" }));
    expect(screen.getByDisplayValue("/api/v1/gitlab/webhooks/7")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Webhook Secret"), "webhook-secret-123456");
    await userEvent.click(screen.getByRole("button", { name: "保存 Webhook Secret" }));

    await waitFor(() => expect(configureWebhookSecret).toHaveBeenCalledWith(7, "webhook-secret-123456"));
    expect(screen.queryByDisplayValue("webhook-secret-123456")).not.toBeInTheDocument();
  });

  it("新增连接成功后直接进入连接测试与仓库绑定配置", async () => {
    listConnections.mockResolvedValue([]);
    createConnection.mockResolvedValue(connection);

    render(<IntegrationsPage />, { wrapper });
    await userEvent.click(await screen.findByRole("button", { name: "添加 GitLab" }));
    await userEvent.type(screen.getByLabelText("连接名称"), "Local GitLab");
    await userEvent.type(screen.getByLabelText("服务地址"), "http://localhost:8929");
    await userEvent.type(screen.getByLabelText("访问令牌"), "never-render-again");
    await userEvent.click(screen.getByRole("button", { name: "添加集成" }));

    await waitFor(() => expect(createConnection).toHaveBeenCalled());
    expect(await screen.findByRole("button", { name: "测试连接" })).toBeInTheDocument();
    expect(screen.queryByDisplayValue("never-render-again")).not.toBeInTheDocument();
  });
});
