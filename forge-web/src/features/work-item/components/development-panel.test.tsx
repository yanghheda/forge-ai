import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { DevelopmentSummaryView } from "./development-panel";

describe("DevelopmentSummaryView", () => {
  it("显示旧 commit 风险并允许完成进行中的任务", () => {
    const complete = vi.fn();
    render(
      <DevelopmentSummaryView
        summary={{
          requirementId: 1,
          ciRequired: true,
          repositoryConfigured: true,
          tasks: [
            {
              id: 2,
              itemKey: "FORGE-2",
              title: "Implement API",
              status: "IN_PROGRESS",
              version: 3,
              branchName: "feature/forge-2",
              branchCommitSha: "new-head",
              mergeRequestId: 7,
              mergeRequestUrl: "https://gitlab.example/mr/7",
              mergeRequestHeadSha: "new-head",
              pipelineId: 9,
              pipelineCommitSha: "old-head",
              pipelineStatus: "success",
              pipelineLastSyncedAt: "2026-09-06T00:00:00Z",
            },
          ],
        }}
        busy={false}
        onStart={vi.fn()}
        onComplete={complete}
      />,
    );

    expect(screen.getByText("Pipeline 不属于 MR 当前 head")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "完成任务" }));
    expect(complete).toHaveBeenCalledWith(2, 3);
  });
});
