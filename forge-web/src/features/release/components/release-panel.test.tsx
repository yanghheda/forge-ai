import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ReleaseCard } from "./release-panel";

describe("ReleaseCard", () => {
  it("shows all backend checks and warns when resource versions changed", () => {
    render(
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
      />,
    );

    expect(screen.getByText("关键资源版本已变化，请重新运行 Precheck。")).toBeInTheDocument();
    expect(screen.getAllByText("FAIL")).toHaveLength(6);
    expect(screen.getByText("QA_PASSED")).toBeInTheDocument();
  });
});
