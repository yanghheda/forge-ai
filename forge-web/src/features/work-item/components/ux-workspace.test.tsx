import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { UxTaskDetailView, UxTaskList } from "./ux-organization";

const task = {
  id: 2,
  organizationId: 10,
  itemKey: "FORGE-2",
  title: "测试需求",
  description: "完善关键交互",
  status: "TODO",
  priority: "MEDIUM",
  version: 0,
};

describe("UX organization", () => {
  it("将 UX Task 渲染为可进入详情的链接", () => {
    render(<UxTaskList organizationSlug="personal" organizationKey="FORGE" tasks={[task]} />);

    expect(screen.getByRole("link", { name: /FORGE-2.*测试需求.*TODO/ })).toHaveAttribute(
      "href",
      "/w/personal/p/FORGE/ux/2",
    );
  });

  it("按照服务端 availableActions 推进 UX Task", () => {
    const transition = vi.fn();
    render(
      <UxTaskDetailView
        task={{ ...task, availableActions: ["START"], guardHints: {} }}
        busy={false}
        onTransition={transition}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "开始处理" }));
    expect(transition).toHaveBeenCalledWith("START");
  });
});
