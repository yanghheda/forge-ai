import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import { RequirementMaterials } from "./requirement-materials";

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient()}>{children}</QueryClientProvider>;
}

describe("RequirementMaterials", () => {
  it("默认展示已保存材料，点击编辑后才显示输入框", () => {
    render(
      <RequirementMaterials
        workItemId={3}
        details={{
          workItemId: 3,
          organizationId: 1,
          goal: "让团队清晰查看需求",
          inScope: "Requirement、PRD 和 UX Spec",
          outOfScope: "技术方案",
          acceptanceCriteria: ["默认只读", "点击后编辑"],
          businessValue: "降低误编辑风险",
          version: 4,
          updatedAt: "2026-09-09T00:00:00Z",
        }}
        onChanged={vi.fn()}
      />,
      { wrapper },
    );

    expect(screen.getByText("让团队清晰查看需求")).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "业务目标" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "编辑需求" }));
    expect(screen.getByRole("textbox", { name: "业务目标" })).toHaveValue("让团队清晰查看需求");
    expect(screen.getByRole("button", { name: "保存材料" })).toBeInTheDocument();
  });
});
