import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter, useLocation } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
  listAgentConversations: vi.fn(),
  getCurrentUser: vi.fn(),
  listConversationRuns: vi.fn(),
  getRunTrace: vi.fn(),
}));

vi.mock("@/features/console", () => ({ listAgentConversations: api.listAgentConversations }));
vi.mock("@/features/auth", () => ({ getCurrentUser: api.getCurrentUser }));
vi.mock("../api/agent-run-api", async () => {
  const actual = await vi.importActual<typeof import("../api/agent-run-api")>("../api/agent-run-api");
  return { ...actual, listConversationRuns: api.listConversationRuns, getRunTrace: api.getRunTrace };
});

import { useRunTraceRefresh } from "../hooks/use-run-trace-refresh";
import type { ConversationRun, RunTrace, TraceNode } from "../api/agent-run-api";
import { AgentTraceWorkspace } from "./trace-workspace";

/* 假 EventSource：记录实例与监听器，供断言订阅创建与关闭。 */
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
}

function conversationRun(overrides: Partial<ConversationRun> = {}): ConversationRun {
  return {
    runId: "r1",
    skill: "PRODUCT",
    workItemId: null,
    status: "COMPLETED",
    terminal: true,
    startedAt: "2026-09-20T10:00:00Z",
    finishedAt: "2026-09-20T10:00:05Z",
    errorCode: null,
    lastSequence: 9,
    triggerMessage: "整理登录需求",
    answer: null,
    ...overrides,
  };
}

function conversation(id: number, title: string, updatedAt: string) {
  return {
    id,
    title,
    requirementId: id === 11 ? 3 : null,
    requirementKey: id === 11 ? "REQ-3" : null,
    requirementTitle: id === 11 ? "登录优化" : null,
    createdAt: updatedAt,
    updatedAt,
    version: 1,
  };
}

function traceNode(overrides: Partial<TraceNode> = {}): TraceNode {
  return {
    key: "seq:1",
    kind: "THINKING",
    title: "模型思考",
    status: null,
    sequence: 1,
    startedAt: null,
    finishedAt: null,
    detail: {},
    ...overrides,
  };
}

function runTrace(nodes: TraceNode[], overrides: Partial<RunTrace> = {}): RunTrace {
  return {
    runId: "r1",
    skill: "PRODUCT",
    workItemId: null,
    status: "COMPLETED",
    terminal: true,
    startedAt: "2026-09-20T10:00:00Z",
    finishedAt: "2026-09-20T10:00:05Z",
    errorCode: null,
    lastSequence: 9,
    nodes,
    ...overrides,
  };
}

function LocationProbe() {
  const location = useLocation();
  return <div data-testid="location">{location.search}</div>;
}

function renderWorkspace() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/agent/trace"]}>{children}</MemoryRouter>
    </QueryClientProvider>
  );
  return render(
    <>
      <LocationProbe />
      <AgentTraceWorkspace />
    </>,
    { wrapper },
  );
}

describe("AgentTraceWorkspace", () => {
  beforeEach(() => {
    FakeEventSource.instances = [];
    globalThis.EventSource = FakeEventSource as unknown as typeof EventSource;
    api.getCurrentUser.mockResolvedValue({ id: 7, email: "a@b.c", displayName: "同事", organization: { id: 42, slug: "acme", name: "Acme", roles: [] } });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("默认选中最近会话，切换会话写入 URL 参数并查询对应轮次", async () => {
    api.listAgentConversations.mockResolvedValue([conversation(11, "最近会话", "2026-09-20T10:00:00Z"), conversation(12, "较早会话", "2026-09-19T10:00:00Z")]);
    api.listConversationRuns.mockResolvedValue([]);

    renderWorkspace();

    await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent("conversationId=11"));
    expect(api.listConversationRuns).toHaveBeenCalledWith(11);

    await userEvent.setup().click(screen.getByRole("button", { name: /较早会话/ }));

    await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent("conversationId=12"));
    expect(api.listConversationRuns).toHaveBeenCalledWith(12);
  });

  it("没有会话时展示左右两侧空态", async () => {
    api.listAgentConversations.mockResolvedValue([]);

    renderWorkspace();

    expect(await screen.findByText("还没有任何会话。")).toBeInTheDocument();
    expect(screen.getByText("暂无会话，请先在 Agent 指令中心创建会话。")).toBeInTheDocument();
    expect(api.listConversationRuns).not.toHaveBeenCalled();
  });

  it("所选会话没有 Run 时展示轮次空态", async () => {
    api.listAgentConversations.mockResolvedValue([conversation(11, "最近会话", "2026-09-20T10:00:00Z")]);
    api.listConversationRuns.mockResolvedValue([]);

    renderWorkspace();

    expect(await screen.findByText("所选会话尚未产生任何执行轮次。")).toBeInTheDocument();
  });

  it("最新一轮默认展开并渲染思考、工具与终态节点", async () => {
    api.listAgentConversations.mockResolvedValue([conversation(11, "最近会话", "2026-09-20T10:00:00Z")]);
    api.listConversationRuns.mockResolvedValue([conversationRun({ runId: "r1" })]);
    api.getRunTrace.mockResolvedValue(
      runTrace([
        traceNode({ key: "seq:1", kind: "THINKING", title: "模型思考 · 计划", detail: { text: "先读取会话上下文", node: "PLAN" } }),
        traceNode({
          key: "seq:2",
          kind: "TOOL",
          title: "工具调用",
          status: "COMPLETED",
          sequence: 2,
          startedAt: "2026-09-20T10:00:01Z",
          finishedAt: "2026-09-20T10:00:03Z",
          detail: { toolName: "read_file", riskLevel: "LOW", status: "COMPLETED", arguments: { path: "/a.ts" }, result: { ok: true } },
        }),
        traceNode({ key: "seq:3", kind: "TERMINAL", title: "运行终态", status: "COMPLETED", sequence: 3, detail: { status: "COMPLETED", durationMs: 5000 } }),
      ]),
    );

    renderWorkspace();

    expect(await screen.findByText("先读取会话上下文")).toBeInTheDocument();
    expect(api.getRunTrace).toHaveBeenCalledWith("r1");
    expect(screen.getByText("read_file · 风险 LOW · 已完成 · 耗时 2 秒")).toBeInTheDocument();
    expect(screen.getByTestId("tool-arguments")).toHaveTextContent('"path": "/a.ts"');
    expect(screen.getByTestId("tool-result")).toHaveTextContent('"ok": true');
    expect(screen.getByText("总耗时")).toBeInTheDocument();
    expect(screen.getByText("5 秒")).toBeInTheDocument();
  });

  it("未知 kind 节点走兜底渲染且不抛错", async () => {
    api.listAgentConversations.mockResolvedValue([conversation(11, "最近会话", "2026-09-20T10:00:00Z")]);
    api.listConversationRuns.mockResolvedValue([conversationRun({ runId: "r1" })]);
    api.getRunTrace.mockResolvedValue(runTrace([traceNode({ key: "seq:9", kind: "BRAND_NEW_EVENT", title: "自定义事件", sequence: 9, detail: { foo: "bar" } })]));

    renderWorkspace();

    await waitFor(() => expect(screen.getByTestId("event-detail")).toBeInTheDocument());
    expect(screen.getByText("通用事件 · 自定义事件")).toBeInTheDocument();
    expect(screen.getByTestId("event-detail")).toHaveTextContent('"foo": "bar"');
  });

  it("进行中的 Run 建立实时订阅", async () => {
    api.listAgentConversations.mockResolvedValue([conversation(11, "最近会话", "2026-09-20T10:00:00Z")]);
    api.listConversationRuns.mockResolvedValue([conversationRun({ runId: "r1", terminal: false, status: "RUNNING", lastSequence: 4 })]);
    api.getRunTrace.mockResolvedValue(runTrace([], { terminal: false, status: "RUNNING" }));

    renderWorkspace();

    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    expect(FakeEventSource.instances[0].url).toContain("/v1/agent-runs/r1/events");
    expect(FakeEventSource.instances[0].closed).toBe(false);
    expect(screen.getByText("实时刷新中")).toBeInTheDocument();
  });
});

describe("useRunTraceRefresh", () => {
  beforeEach(() => {
    FakeEventSource.instances = [];
    globalThis.EventSource = FakeEventSource as unknown as typeof EventSource;
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  function RefreshProbe({ run }: { run: ConversationRun }) {
    useRunTraceRefresh(42, run);
    return null;
  }

  it("进行中订阅 SSE，进入终态后关闭连接", () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const running = conversationRun({ runId: "r1", terminal: false, status: "RUNNING", lastSequence: 4 });
    const { rerender } = render(
      <QueryClientProvider client={queryClient}>
        <RefreshProbe run={running} />
      </QueryClientProvider>,
    );

    expect(FakeEventSource.instances).toHaveLength(1);
    expect(FakeEventSource.instances[0].url).toBe("/api/v1/agent-runs/r1/events?organizationId=42&afterSequence=4");
    expect(FakeEventSource.instances[0].closed).toBe(false);

    rerender(
      <QueryClientProvider client={queryClient}>
        <RefreshProbe run={{ ...running, terminal: true }} />
      </QueryClientProvider>,
    );

    expect(FakeEventSource.instances[0].closed).toBe(true);
    expect(FakeEventSource.instances).toHaveLength(1);
  });
});
