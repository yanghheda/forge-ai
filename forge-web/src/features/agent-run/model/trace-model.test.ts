import { describe, expect, it } from "vitest";

import type { TraceNode } from "../api/agent-run-api";
import { FALLBACK_NODE_KIND, TRACE_NODE_KINDS, formatDuration, formatNodeTime, nodePresentation, readNumber, readRecord, readText, readTextList } from "./trace-model";

/* 构造一份最小可用轨迹节点，测试按需覆盖字段。 */
function node(overrides: Partial<TraceNode> = {}): TraceNode {
  return {
    key: "seq:1",
    kind: "STEP",
    title: "",
    status: null,
    sequence: 1,
    startedAt: null,
    finishedAt: null,
    detail: {},
    ...overrides,
  };
}

describe("nodePresentation", () => {
  it("已知 kind 分派到对应中文标签与展示形态", () => {
    const thinking = nodePresentation(node({ kind: "THINKING", title: "选择工具", detail: { text: "先看需求" } }));
    expect(thinking.kind).toBe("THINKING");
    expect(thinking.icon).toBe("thinking");
    expect(thinking.collapsible).toBe(true);
    expect(thinking.structured).toBe(false);
    expect(thinking.text).toBe("先看需求");
    expect(thinking.title).toContain("模型思考");
    expect(thinking.title).toContain("选择工具");

    const reasoning = nodePresentation(node({ kind: "REASONING", detail: { text: "回顾上下文" } }));
    expect(reasoning.kind).toBe("REASONING");
    expect(reasoning.title).toContain("执行思路");
    expect(reasoning.text).toBe("回顾上下文");

    const answer = nodePresentation(node({ kind: "ANSWER", detail: { text: "已完成" } }));
    expect(answer.kind).toBe("ANSWER");
    expect(answer.title).toContain("Agent 回答");
  });

  it("结构化节点不读取文本且标记为 JSON 展示", () => {
    const tool = nodePresentation(node({ kind: "TOOL", title: "search", detail: { text: "不应作为正文", toolName: "search" } }));
    expect(tool.kind).toBe("TOOL");
    expect(tool.structured).toBe(true);
    expect(tool.collapsible).toBe(false);
    expect(tool.text).toBeNull();
  });

  it("已知 kind 全部归一到自身", () => {
    for (const kind of TRACE_NODE_KINDS) {
      expect(nodePresentation(node({ kind })).kind).toBe(kind);
    }
  });

  it("未知 kind 兜底为通用事件并保留原始 kind、标题与完整 detail", () => {
    const detail = { eventType: "sandbox.snapshot", payload: { id: 7 }, nested: { a: 1 } };
    const presentation = nodePresentation(node({ kind: "FUTURE_KIND", title: "未来节点", detail }));

    expect(presentation.kind).toBe(FALLBACK_NODE_KIND);
    expect(presentation.rawKind).toBe("FUTURE_KIND");
    expect(presentation.rawTitle).toBe("未来节点");
    expect(presentation.title).toContain("通用事件");
    expect(presentation.title).toContain("未来节点");
    expect(presentation.structured).toBe(true);
    expect(presentation.collapsible).toBe(false);
    expect(presentation.detail).toBe(detail);
  });

  it("未知 kind 且无标题时回退到通用事件标签", () => {
    const presentation = nodePresentation(node({ kind: "FUTURE_KIND", title: "" }));
    expect(presentation.title).toBe("通用事件");
    expect(presentation.rawKind).toBe("FUTURE_KIND");
  });

  it("标题已含类型标签时不重复拼接", () => {
    const presentation = nodePresentation(node({ kind: "THINKING", title: "模型思考" }));
    expect(presentation.title).toBe("模型思考");
  });
});

describe("readText 等安全读取工具", () => {
  it("类型匹配时返回字段值", () => {
    const detail = { text: "内容", count: 3, meta: { a: 1 }, steps: ["one", "two", 3] };
    expect(readText(detail, "text")).toBe("内容");
    expect(readNumber(detail, "count")).toBe(3);
    expect(readRecord(detail, "meta")).toEqual({ a: 1 });
    expect(readTextList(detail, "steps")).toEqual(["one", "two"]);
  });

  it("字段缺失或类型不符时不抛异常并回退安全默认值", () => {
    const detail = { text: 12, meta: [1, 2], steps: "not-array" };
    expect(readText(detail, "missing")).toBeNull();
    expect(readText(detail, "text")).toBeNull();
    expect(readNumber(detail, "missing")).toBeNull();
    expect(readRecord(detail, "meta")).toBeNull();
    expect(readTextList(detail, "steps")).toEqual([]);
    expect(readText(null, "text")).toBeNull();
    expect(readNumber(undefined, "count")).toBeNull();
    expect(readRecord(null, "meta")).toBeNull();
    expect(readTextList(undefined, "steps")).toEqual([]);
  });
});

describe("formatDuration", () => {
  it("任一时间缺失时返回 null", () => {
    expect(formatDuration(null, "2026-09-05T00:00:01Z")).toBeNull();
    expect(formatDuration("2026-09-05T00:00:01Z", null)).toBeNull();
    expect(formatDuration(undefined, undefined)).toBeNull();
  });

  it("同秒内按毫秒展示", () => {
    expect(formatDuration("2026-09-05T00:00:00Z", "2026-09-05T00:00:00Z")).toBe("0 毫秒");
    expect(formatDuration("2026-09-05T00:00:00Z", "2026-09-05T00:00:00.500Z")).toBe("500 毫秒");
  });

  it("秒级按秒展示并按需保留一位小数", () => {
    expect(formatDuration("2026-09-05T00:00:00Z", "2026-09-05T00:00:02Z")).toBe("2 秒");
    expect(formatDuration("2026-09-05T00:00:00Z", "2026-09-05T00:00:01.500Z")).toBe("1.5 秒");
  });

  it("跨分钟与跨小时按中文单位展示", () => {
    expect(formatDuration("2026-09-05T00:00:00Z", "2026-09-05T00:01:30Z")).toBe("1 分 30 秒");
    expect(formatDuration("2026-09-05T00:00:00Z", "2026-09-05T00:02:00Z")).toBe("2 分");
    expect(formatDuration("2026-09-05T00:00:00Z", "2026-09-05T01:00:00Z")).toBe("1 小时");
    expect(formatDuration("2026-09-05T00:00:00Z", "2026-09-05T01:05:00Z")).toBe("1 小时 5 分");
  });

  it("时间非法或倒序时返回 null", () => {
    expect(formatDuration("not-a-date", "2026-09-05T00:00:02Z")).toBeNull();
    expect(formatDuration("2026-09-05T00:00:02Z", "2026-09-05T00:00:00Z")).toBeNull();
  });
});

describe("formatNodeTime", () => {
  it("缺失或非法时间返回 null", () => {
    expect(formatNodeTime(null)).toBeNull();
    expect(formatNodeTime(undefined)).toBeNull();
    expect(formatNodeTime("")).toBeNull();
    expect(formatNodeTime("not-a-date")).toBeNull();
  });

  it("合法时间格式化为中文月日时分", () => {
    const formatted = formatNodeTime("2026-09-05T00:00:00Z");
    expect(formatted).toBeTruthy();
    expect(formatted).toContain(":");
  });
});
