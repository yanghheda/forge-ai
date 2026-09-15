import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { useTypewriterText } from "./use-typewriter-text";

describe("useTypewriterText", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("按字符逐帧展示不断增长的流式正文", () => {
    vi.useFakeTimers();
    const { result, rerender } = renderHook(({ target }) => useTypewriterText(target, "run-1", 20), { initialProps: { target: "需求" } });

    expect(result.current).toBe("");
    act(() => vi.advanceTimersByTime(20));
    expect(result.current).toBe("需");

    rerender({ target: "需求已完成" });
    act(() => vi.advanceTimersByTime(80));
    expect(result.current).toBe("需求已完成");
  });

  it("切换 Run 时不会泄漏上一轮正文", () => {
    vi.useFakeTimers();
    const { result, rerender } = renderHook(({ runId, target }) => useTypewriterText(target, runId, 20), { initialProps: { runId: "run-1", target: "旧回答" } });
    act(() => vi.advanceTimersByTime(20));
    expect(result.current).toBe("旧");

    rerender({ runId: "run-2", target: "新回答" });
    expect(result.current).toBe("");
    act(() => vi.advanceTimersByTime(20));
    expect(result.current).toBe("新");
  });
});
