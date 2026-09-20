import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { AgentMessageBody } from "./agent-message-body";

describe("AgentMessageBody", () => {
  it("把 Agent 的 Markdown 正文渲染为语义化 HTML", () => {
    const { container } = render(<AgentMessageBody content={"你好 **ForgeAI**\n\n- 第一项\n- 第二项"} />);

    expect(screen.getByText("ForgeAI").tagName).toBe("STRONG");
    expect(container.querySelectorAll("li")).toHaveLength(2);
  });
});
