import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useNotificationStream } from "./use-notification-stream";

/* 假 EventSource：记录实例与监听器，并提供 emit 触发事件，供断言连接与重连行为。 */
class FakeEventSource {
  static instances: FakeEventSource[] = [];
  readonly url: string;
  closed = false;
  onerror: ((event: Event) => void) | null = null;
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

  /* 触发指定类型事件，lastEventId 模拟服务端事件帧 id，用于推动本地游标。 */
  emit(type: string, lastEventId: number) {
    const message = { lastEventId: String(lastEventId), data: "{}" } as MessageEvent<string>;
    for (const listener of this.listeners.get(type) ?? []) {
      listener(message as unknown as Event);
    }
  }
}

/* 探针组件：仅驱动 hook，便于在查询客户端上下文中断言推送行为。 */
function NotificationStreamProbe({ enabled = true }: { enabled?: boolean }) {
  useNotificationStream(enabled);
  return <span />;
}

function renderProbe(queryClient: QueryClient, enabled = true) {
  return render(
    <QueryClientProvider client={queryClient}>
      <NotificationStreamProbe enabled={enabled} />
    </QueryClientProvider>,
  );
}

describe("useNotificationStream", () => {
  beforeEach(() => {
    FakeEventSource.instances = [];
    vi.stubGlobal("EventSource", FakeEventSource);
    vi.useFakeTimers();
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it("收到 notification 事件后去抖失效 notifications 查询", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");
    renderProbe(queryClient);

    act(() => {
      FakeEventSource.instances[0].emit("notification", 7);
    });
    expect(invalidate).not.toHaveBeenCalled();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(300);
    });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["notifications"] });
  });

  it("onerror 关闭旧连接并按最新游标重连", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    renderProbe(queryClient);
    const first = FakeEventSource.instances[0];
    act(() => {
      first.emit("notification", 12);
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(300);
    });

    act(() => {
      first.onerror?.(new Event("error"));
    });
    expect(first.closed).toBe(true);
    expect(FakeEventSource.instances).toHaveLength(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(500);
    });
    expect(FakeEventSource.instances).toHaveLength(2);
    expect(FakeEventSource.instances[1].url).toContain("afterId=12");
  });

  it("卸载时关闭连接且不再重连", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { unmount } = renderProbe(queryClient);
    const source = FakeEventSource.instances[0];

    unmount();

    expect(source.closed).toBe(true);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    expect(FakeEventSource.instances).toHaveLength(1);
  });

  it("enabled=false 时不创建连接", () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    renderProbe(queryClient, false);

    expect(FakeEventSource.instances).toHaveLength(0);
  });
});
