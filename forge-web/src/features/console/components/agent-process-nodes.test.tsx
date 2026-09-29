import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import type { TraceNode } from "@/features/agent-run";

import { AgentProcessNodes } from "./agent-process-nodes";

function node(overrides: Partial<TraceNode> = {}): TraceNode {
  return {
    key: "seq:1",
    kind: "THINKING",
    title: "",
    status: null,
    sequence: 1,
    startedAt: null,
    finishedAt: null,
    detail: {},
    ...overrides,
  };
}

/* 构造一个工具调用节点，便于校验分组摘要与卡片内容。 */
function tool(overrides: Partial<TraceNode> = {}): TraceNode {
  return node({ kind: "TOOL", title: "工具调用", status: "COMPLETED", detail: { toolName: "read_file", status: "COMPLETED" }, ...overrides });
}

describe("AgentProcessNodes", () => {
  afterEach(cleanup);

  it("整体渲染为可折叠的「思考过程」区块", () => {
    render(<AgentProcessNodes nodes={[node({ key: "t1", kind: "THINKING", title: "模型思考", detail: { text: "先读取会话上下文" } })]} />);

    expect(screen.getByText("思考过程").closest("details")).toHaveProperty("open", true);
  });

  it("THINKING 与 REASONING 分别渲染为可折叠节点并保留正文", () => {
    render(<AgentProcessNodes nodes={[node({ key: "t1", kind: "THINKING", title: "模型思考", detail: { text: "先读取会话上下文" } }), node({ key: "r1", kind: "REASONING", title: "执行思路", detail: { text: "分两步落地" } })]} />);

    expect(screen.getByText("模型思考").closest("details")).toHaveProperty("open", true);
    expect(screen.getByText("先读取会话上下文")).toBeInTheDocument();
    expect(screen.getByText("执行思路").closest("details")).toHaveProperty("open", true);
    expect(screen.getByText("分两步落地")).toBeInTheDocument();
  });

  it("PLAN 渲染其步骤列表", () => {
    render(<AgentProcessNodes nodes={[node({ key: "p1", kind: "PLAN", title: "执行计划", detail: { steps: ["读取需求", "生成方案"] } })]} />);

    expect(screen.getByText("读取需求")).toBeInTheDocument();
    expect(screen.getByText("生成方案")).toBeInTheDocument();
  });

  it("STEP 渲染步骤标签与状态耗时", () => {
    render(<AgentProcessNodes nodes={[node({ key: "s1", kind: "STEP", title: "执行步骤", status: "COMPLETED", startedAt: "2026-09-20T10:00:00Z", finishedAt: "2026-09-20T10:00:02Z", detail: { stepNo: 1, name: "读取需求" } })]} />);

    expect(screen.getByText("第 1 步 · 读取需求")).toBeInTheDocument();
    expect(screen.getByText("已完成 · 2 秒")).toBeInTheDocument();
  });

  it("TOOL 聚为一组，摘要行给出调用数量并渲染参数 / 结果 JSON", () => {
    render(
      <AgentProcessNodes
        nodes={[
          tool({
            key: "tool1",
            startedAt: "2026-09-20T10:00:00Z",
            finishedAt: "2026-09-20T10:00:02Z",
            detail: { toolName: "read_file", status: "COMPLETED", arguments: { path: "/a.ts" }, result: { ok: true } },
          }),
        ]}
      />,
    );

    expect(screen.getByText("已调用 1 个工具")).toBeInTheDocument();
    expect(screen.getByText("read_file")).toBeInTheDocument();
    expect(screen.getByText("已完成 · 2 秒")).toBeInTheDocument();
    expect(screen.getByTestId("tool-arguments")).toHaveTextContent('"path": "/a.ts"');
    expect(screen.getByTestId("tool-result")).toHaveTextContent('"ok": true');
  });

  it("连续工具调用归为一个分组并统计失败数量", () => {
    render(<AgentProcessNodes nodes={[tool({ key: "t1" }), tool({ key: "t2", status: "FAILED" }), tool({ key: "t3", status: "RUNNING", finishedAt: null })]} />);

    expect(screen.getByText("已调用 3 个工具 · 失败 1 · 进行中 1")).toBeInTheDocument();
    expect(screen.getAllByText(/^已调用 \d+ 个工具/)).toHaveLength(1);
  });

  it("工具调用被其他节点打断时拆分为多个分组", () => {
    render(<AgentProcessNodes nodes={[tool({ key: "t1" }), node({ key: "think", kind: "THINKING", title: "模型思考", detail: { text: "中间思考" } }), tool({ key: "t2" })]} />);

    expect(screen.getAllByText("已调用 1 个工具")).toHaveLength(2);
  });

  it("APPROVAL 渲染审批卡片", () => {
    render(<AgentProcessNodes nodes={[node({ key: "a1", kind: "APPROVAL", title: "审批决策", detail: { approvalId: "ap-1", toolName: "deploy", riskLevel: "HIGH", status: "PENDING", reason: "上线生产" } })]} />);

    expect(screen.getByText("deploy")).toBeInTheDocument();
    expect(screen.getByText("风险 HIGH")).toBeInTheDocument();
    expect(screen.getByText("待处理")).toBeInTheDocument();
    expect(screen.getByText("上线生产")).toBeInTheDocument();
  });

  it("未知 kind 走通用事件渲染且不抛错", () => {
    render(<AgentProcessNodes nodes={[node({ key: "e1", kind: "BRAND_NEW", title: "自定义事件", detail: { foo: "bar" } })]} />);

    expect(screen.getByText("通用事件 · 自定义事件")).toBeInTheDocument();
    expect(screen.getByTestId("event-detail")).toHaveTextContent('"foo": "bar"');
  });

  it("不渲染 ANSWER 与 TERMINAL 节点", () => {
    const { container } = render(<AgentProcessNodes nodes={[node({ key: "ans", kind: "ANSWER", title: "Agent 回答", detail: { text: "最终答案" } }), node({ key: "term", kind: "TERMINAL", title: "运行终态", detail: { status: "COMPLETED" } })]} />);

    expect(container).toBeEmptyDOMElement();
  });

  it("无可见节点时不渲染任何内容", () => {
    const { container } = render(<AgentProcessNodes nodes={[]} />);

    expect(container).toBeEmptyDOMElement();
  });
});
