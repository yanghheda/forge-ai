import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/features/auth", () => ({
  getCurrentUser: vi.fn().mockResolvedValue({ id: 7, organization: { id: 10 } }),
}));
vi.mock("../api/document-api", () => ({
  listWorkItemDocuments: vi.fn().mockResolvedValue([]),
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

import { canSubmitProductReview, PrdWorkspace } from "./prd-workspace";

describe("PrdWorkspace", () => {
  it("没有 PRD 时展示创建入口，并始终展示产品推进路径", async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <PrdWorkspace requirementId={1} />
      </QueryClientProvider>,
    );

    expect(await screen.findByRole("button", { name: "创建 PRD 文档" })).toBeInTheDocument();
    expect(screen.getByText("产品推进")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "提交产品评审" })).toHaveLength(2);
    expect(screen.getAllByRole("button", { name: "提交产品评审" }).every((button) => button.hasAttribute("disabled"))).toBe(true);
    expect(screen.getByText(/请先发布 PRD 版本/)).toBeInTheDocument();
  });

  it("PRD 已发布但需求描述缺失时仍禁止提交", () => {
    expect(canSubmitProductReview(true, true, ["description"])).toBe(false);
    expect(canSubmitProductReview(true, true, [])).toBe(true);
  });
});
