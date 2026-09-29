import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useConversationStream } from "./use-conversation-stream";

/* 假 EventSource：记录实例与监听器，并提供 emit 触发事件，供断言订阅与增量累积。 */
class FakeEventSource {
  static instances: FakeEventSource[] = [];
  readonly url: string;
  closed = false;
  private readonly listeners = new Map<string, Set<(event: Event) => void>>();

  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, callback: (event: Event) => void) {
    const set = this.listeners.get(type) ?? new Set<(event: Event) => void>();
    set.add(callback);
    this.listeners.set(type, set);
  }

  removeEventListener() {}

  close() {
    this.closed = true;
  }

  emit(type: string, payload: Record<string, unknown>, sequence: number) {
    const message = { data: JSON.stringify({ sequence, type, payload }) } as MessageEvent<string>;
    for (const listener of this.listeners.get(type) ?? []) {
      listener(message as unknown as Event);
    }
  }
}

function StreamProbe() {
  const stream = useConversationStream(42, "r1");
  return <span data-testid="answer">{stream.answer}</span>;
}

describe("useConversationStream", () => {
  beforeEach(() => {
    FakeEventSource.instances = [];
    globalThis.EventSource = FakeEventSource as unknown as typeof EventSource;
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("message.delta 事件累积为 answer", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <StreamProbe />
      </QueryClientProvider>,
    );

    const source = FakeEventSource.instances[0];
    act(() => {
      source.emit("message.delta", { delta: "你好" }, 1);
      source.emit("message.delta", { delta: "世界" }, 2);
    });

    await waitFor(() => expect(screen.getByTestId("answer")).toHaveTextContent("你好世界"));
  });

  it("工具完成事件去抖刷新 run-trace 查询", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");
    render(
      <QueryClientProvider client={queryClient}>
        <StreamProbe />
      </QueryClientProvider>,
    );

    act(() => {
      FakeEventSource.instances[0].emit("tool.completed", { toolName: "read_file", status: "COMPLETED" }, 1);
    });

    await waitFor(() => expect(invalidate).toHaveBeenCalledWith({ queryKey: ["run-trace", "r1"] }));
  });
});
