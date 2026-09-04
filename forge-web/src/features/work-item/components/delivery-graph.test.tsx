import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { DeliveryGraphView } from "./delivery-graph";

describe("DeliveryGraphView", () => {
  it("同时提供关系图和可访问列表，并显示截断提示", () => {
    render(
      <DeliveryGraphView
        graph={{
          nodes: [
            {
              id: "work-item:1",
              kind: "WORK_ITEM",
              resourceId: 1,
              type: "REQUIREMENT",
              title: "FORGE-1 · 手机号登录",
              status: "READY_FOR_DEV",
              depth: 0,
            },
          ],
          edges: [],
          truncated: true,
          maxDepth: 8,
          maxNodes: 500,
        }}
      />,
    );

    expect(screen.getByRole("img", { name: "交付关系图" })).toBeInTheDocument();
    expect(screen.getByRole("list", { name: "交付节点列表" })).toHaveTextContent("手机号登录");
    expect(screen.getByText(/节点 500/)).toBeInTheDocument();
  });
});
