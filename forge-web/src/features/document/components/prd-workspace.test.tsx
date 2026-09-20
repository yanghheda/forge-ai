import { cleanup, render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/features/auth", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/auth")>()),
  getCurrentUser: vi.fn().mockResolvedValue({ id: 7, organization: { id: 10, roles: ["PRODUCT"] } }),
}));
vi.mock("../api/document-api", () => ({
  listWorkItemDocuments: vi.fn(),
  createDocument: vi.fn(),
  listDocumentVersions: vi.fn(),
  saveDocumentVersion: vi.fn(),
  publishDocumentVersion: vi.fn(),
}));
vi.mock("@/features/work-item/api/work-item-api", () => ({
  getOrganizationRequirement: vi.fn().mockResolvedValue({ id: 1, itemKey: "DEMO-1", title: "黄金 Demo", status: "DRAFT", version: 0, updatedAt: "2026-09-12T04:00:00Z" }),
  getRequirementWorkflow: vi.fn().mockResolvedValue({ availableActions: ["SUBMIT_PRODUCT_REVIEW"], guardHints: { SUBMIT_PRODUCT_REVIEW: ["publishedPrd"] } }),
  transitionRequirementWorkflow: vi.fn(),
  getRequirementActivity: vi.fn().mockResolvedValue([]),
  createRequirementComment: vi.fn(),
}));
vi.mock("@/features/agent-run/api/agent-run-api", () => ({ createAgentRun: vi.fn() }));
vi.mock("../editor/document-editor", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../editor/document-editor")>()),
  DocumentEditor: () => (
    <section aria-label="文档编辑器">
      <div className="paper">
        <h2>1 背景与目标</h2>
        <h3>1.1 现状</h3>
        <h2>2 用户故事</h2>
      </div>
    </section>
  ),
}));

import { getCurrentUser } from "@/features/auth";
import { listDocumentVersions, listWorkItemDocuments } from "../api/document-api";
import { PrdWorkspace } from "./prd-workspace";

const scrollIntoView = vi.fn();

function renderWorkspace() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <PrdWorkspace requirementId={1} />
    </QueryClientProvider>,
  );
}

describe("PrdWorkspace", () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.clearAllMocks();
    // 恢复默认的产品角色，避免单个用例改 mock 后污染后续用例。
    vi.mocked(getCurrentUser).mockResolvedValue({ id: 7, email: "pm@example.com", displayName: "钱产品", organization: { id: 10, slug: "demo", name: "Demo", roles: ["PRODUCT"] } });
    Element.prototype.scrollIntoView = scrollIntoView;
    vi.mocked(listWorkItemDocuments).mockResolvedValue([]);
  });

  it("没有 PRD 时展示创建入口，并移除右侧推进与 Agent 模块", async () => {
    renderWorkspace();

    expect(await screen.findByRole("button", { name: "创建 PRD 文档" })).toBeInTheDocument();
    expect(screen.queryByText("产品推进")).not.toBeInTheDocument();
    expect(screen.queryByText("ProductAgent")).not.toBeInTheDocument();
    expect(screen.getByText("最近更新")).toBeInTheDocument();
    expect(screen.getByText("需求状态")).toBeInTheDocument();
  });

  it("点击目录项滚动到对应标题并标记当前章节", async () => {
    vi.mocked(listWorkItemDocuments).mockResolvedValue([{ id: 5, organizationId: 10, workItemId: 1, type: "PRD", title: "DEMO-1 黄金 Demo 产品需求文档", status: "DRAFT", currentVersionId: 9, version: 0 }]);
    vi.mocked(listDocumentVersions).mockResolvedValue([
      {
        id: 9,
        documentId: 5,
        versionNo: 2,
        content: {
          type: "doc",
          content: [
            { type: "heading", attrs: { level: 2 }, content: [{ type: "text", text: "1 背景与目标" }] },
            { type: "heading", attrs: { level: 3 }, content: [{ type: "text", text: "1.1 现状" }] },
            { type: "heading", attrs: { level: 2 }, content: [{ type: "text", text: "2 用户故事" }] },
          ],
        },
        plainText: "1 背景与目标\n1.1 现状\n2 用户故事",
        contentHash: "hash",
        createdAt: "2026-09-12T04:00:00Z",
      },
    ]);
    renderWorkspace();

    const third = await screen.findByRole("button", { name: "2 用户故事" });
    expect(screen.getByRole("button", { name: "1 背景与目标" })).toHaveAttribute("aria-current", "true");

    fireEvent.click(third);

    // 正文标题不带 id，跳转只能按序号定位到第 3 个标题。
    await waitFor(() => expect(scrollIntoView).toHaveBeenCalledTimes(1));
    expect((scrollIntoView.mock.instances[0] as unknown as HTMLElement).textContent).toBe("2 用户故事");
    expect(third).toHaveAttribute("aria-current", "true");
    expect(screen.getByRole("button", { name: "1 背景与目标" })).not.toHaveAttribute("aria-current");
  });

  it("非产品角色只读：隐藏创建、发布与 AI 润色入口", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue({ id: 7, email: "qa@example.com", displayName: "赵测试", organization: { id: 10, slug: "demo", name: "Demo", roles: ["QA"] } });
    vi.mocked(listWorkItemDocuments).mockResolvedValue([{ id: 5, organizationId: 10, workItemId: 1, type: "PRD", title: "DEMO-1 黄金 Demo 产品需求文档", status: "DRAFT", currentVersionId: 9, version: 0 }]);
    vi.mocked(listDocumentVersions).mockResolvedValue([]);
    renderWorkspace();

    expect(await screen.findByText("只读")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "AI 润色" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "发布当前版本" })).not.toBeInTheDocument();
  });

  it("非产品角色且没有 PRD 时不提供创建入口", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue({ id: 7, email: "qa@example.com", displayName: "赵测试", organization: { id: 10, slug: "demo", name: "Demo", roles: ["QA"] } });
    vi.mocked(listWorkItemDocuments).mockResolvedValue([]);
    renderWorkspace();

    expect(await screen.findByText("只读")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "创建 PRD 文档" })).not.toBeInTheDocument();
  });
});
