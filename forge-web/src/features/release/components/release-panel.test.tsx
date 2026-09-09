import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { DeploymentCard, ReleaseCard } from "./release-panel";

describe("ReleaseCard", () => {
  it("shows all backend checks and warns when resource versions changed", () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <ReleaseCard
        release={{
          id: 1,
          workspaceId: 2,
          projectId: 3,
          versionName: "v1.0.0",
          environment: "production",
          status: "PRECHECKED",
          releaseNote: "notes",
          itemIds: [4],
          version: 1,
          latestPrecheck: {
            id: 5,
            status: "FAIL",
            current: false,
            checkedAt: "2026-09-06T00:00:00Z",
            checkedByType: "USER",
            checkedById: 6,
            resourceVersions: { release: 1 },
            checks: [
              "WORK_ITEMS_READY",
              "PIPELINE_GREEN",
              "QA_PASSED",
              "NO_BLOCKING_BUGS",
              "ARTIFACTS_PRESENT",
              "APPROVAL_POLICY",
            ].map((rule) => ({ rule: rule as never, passed: false, details: ["missing"] })),
          },
        }}
        onSaveNote={vi.fn()}
        onPrecheck={vi.fn()}
        />
      </QueryClientProvider>,
    );

    expect(screen.getByText("关键资源版本已变化，请重新运行预检。")).toBeInTheDocument();
    expect(screen.getAllByText("未通过")).toHaveLength(6);
    expect(screen.getByText("QA_PASSED")).toBeInTheDocument();
    expect(screen.getByText("仅模拟执行：不会连接或改变生产环境。")).toBeInTheDocument();
  });

  it("labels deployment as simulated and exposes explicit approval decisions", () => {
    render(
      <DeploymentCard
        deployment={{
          id: 8,
          releaseId: 1,
          mode: "SIMULATED",
          status: "PENDING_APPROVAL",
          requestedBy: 10,
          approverUserId: null,
          approvalExpiresAt: "2026-09-07T02:00:00Z",
          resultCode: null,
          resultSummary: null,
          version: 0,
        }}
        onDecide={vi.fn()}
      />,
    );

    expect(screen.getByText("模拟部署 · 非生产环境")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "批准" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "拒绝" })).toBeInTheDocument();
  });
});
