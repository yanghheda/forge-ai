import { describe, expect, it } from "vitest";

import { isLiveRunPlaybackEnabled, isStreamPlaybackVisible } from "./conversation-answer-playback";

describe("isLiveRunPlaybackEnabled", () => {
  it("刷新或切换到已完成的历史 Run 时不播放", () => {
    expect(isLiveRunPlaybackEnabled({ activeConversationId: 1, effectiveRunId: "run-finished", isRunActive: false, latestRunConversationId: undefined, latestRunId: undefined })).toBe(false);
  });

  it("本页刚发起的 Run 完成后仍允许把缓冲正文播放完", () => {
    expect(isLiveRunPlaybackEnabled({ activeConversationId: 1, effectiveRunId: "run-live", isRunActive: false, latestRunConversationId: 1, latestRunId: "run-live" })).toBe(true);
  });
});

describe("isStreamPlaybackVisible", () => {
  it("持久化消息先到达时仍保留尚未播放完的打字机正文", () => {
    expect(isStreamPlaybackVisible({ hasAnswer: true, isTyping: true }, true)).toBe(true);
  });

  it("打字机追上完整正文后切换回持久化消息", () => {
    expect(isStreamPlaybackVisible({ hasAnswer: true, isTyping: false }, true)).toBe(false);
  });

  it("持久化消息尚未刷新时保留已播放完的流式正文", () => {
    expect(isStreamPlaybackVisible({ hasAnswer: true, isTyping: false }, false)).toBe(true);
  });
});
