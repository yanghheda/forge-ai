import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import styles from "./console.module.css";
import { ReasoningDetails } from "./agent-message";

describe("ReasoningDetails", () => {
  afterEach(cleanup);

  it("只在思考进行中展示扫光文字", () => {
    const { rerender } = render(<ReasoningDetails reasoning={[]} completed={false} />);

    expect(screen.getByText("正在思考")).toHaveClass(styles.reasoningSweep);

    rerender(<ReasoningDetails reasoning={[]} completed />);
    expect(screen.getByText("已完成思考")).not.toHaveClass(styles.reasoningSweep);
  });

  it("思考完成后仍展开展示思考内容", () => {
    render(<ReasoningDetails reasoning={["先读取会话上下文"]} completed />);

    expect(screen.getByText("已完成思考")).toBeInTheDocument();
    expect(screen.getByText("先读取会话上下文").closest("details")).toHaveProperty("open", true);
  });
});
