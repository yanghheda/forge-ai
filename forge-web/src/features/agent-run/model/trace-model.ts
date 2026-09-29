import type { TraceNode } from "../api/agent-run-api";

/* 轨迹节点已知类型；后端新增类型时前端统一按 EVENT 兜底，不丢弃节点。 */
export const TRACE_NODE_KINDS = ["THINKING", "REASONING", "PLAN", "STEP", "TOOL", "APPROVAL", "ANSWER", "TERMINAL", "EVENT"] as const;

export type TraceNodeKind = (typeof TRACE_NODE_KINDS)[number];

/* 未知类型统一归入的兜底节点类型。 */
export const FALLBACK_NODE_KIND: TraceNodeKind = "EVENT";

/* 各已知类型对应的展示元信息：中文标签、图标语义 key 与展示形态。 */
const presentations: Record<TraceNodeKind, { label: string; icon: string; collapsible: boolean; structured: boolean }> = {
  THINKING: { label: "模型思考", icon: "thinking", collapsible: true, structured: false },
  REASONING: { label: "执行思路", icon: "reasoning", collapsible: true, structured: false },
  PLAN: { label: "执行计划", icon: "plan", collapsible: false, structured: true },
  STEP: { label: "执行步骤", icon: "step", collapsible: false, structured: true },
  TOOL: { label: "工具调用", icon: "tool", collapsible: false, structured: true },
  APPROVAL: { label: "审批决策", icon: "approval", collapsible: false, structured: true },
  ANSWER: { label: "Agent 回答", icon: "answer", collapsible: true, structured: false },
  TERMINAL: { label: "运行终态", icon: "terminal", collapsible: false, structured: true },
  EVENT: { label: "通用事件", icon: "event", collapsible: false, structured: true },
};

export interface NodePresentation {
  /* 归一化节点类型；未知类型统一为 EVENT，便于页面走通用渲染 */
  kind: TraceNodeKind;
  /* 后端返回的原始 kind，未知类型时保留用于排查 */
  rawKind: string;
  /* 中文展示标题，已合并节点类型标签与节点原始标题 */
  title: string;
  /* 节点原始标题，未知类型时原样保留 */
  rawTitle: string;
  /* 图标语义 key，页面据此映射具体图标 */
  icon: string;
  /* 是否为折叠文本类节点（思考 / 思路 / 回答） */
  collapsible: boolean;
  /* 是否为结构化 JSON 展示节点 */
  structured: boolean;
  /* 文本类节点的正文；结构化节点为 null */
  text: string | null;
  /* 节点完整 detail 原文，未知类型兜底时同样不丢失 */
  detail: Record<string, unknown>;
}

function isTraceNodeKind(value: string): value is TraceNodeKind {
  return (TRACE_NODE_KINDS as readonly string[]).includes(value);
}

/* 安全读取 detail 中的字符串字段；字段缺失或类型不符时返回 null。 */
export function readText(detail: Record<string, unknown> | null | undefined, key: string): string | null {
  const value = detail?.[key];
  return typeof value === "string" ? value : null;
}

/* 安全读取 detail 中的数字字段；字段缺失或类型不符时返回 null。 */
export function readNumber(detail: Record<string, unknown> | null | undefined, key: string): number | null {
  const value = detail?.[key];
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

/* 安全读取 detail 中的对象字段；字段缺失、为数组或类型不符时返回 null。 */
export function readRecord(detail: Record<string, unknown> | null | undefined, key: string): Record<string, unknown> | null {
  const value = detail?.[key];
  return typeof value === "object" && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : null;
}

/* 安全读取 detail 中的字符串数组字段；字段缺失或类型不符时返回空数组。 */
export function readTextList(detail: Record<string, unknown> | null | undefined, key: string): string[] {
  const value = detail?.[key];
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

/* 合并节点类型标签与节点标题，避免重复或空标题。 */
function combineTitle(label: string, rawTitle: string): string {
  const trimmed = rawTitle?.trim() ?? "";
  if (!trimmed || trimmed === label || trimmed.startsWith(label)) {
    return trimmed || label;
  }
  return `${label} · ${trimmed}`;
}

/*
 * 计算节点展示信息：已知 kind 分派到对应中文标签与图标，
 * 未知 kind 回退为「通用事件 + JSON」并保留原始 kind、标题与完整 detail，不抛异常也不丢弃节点。
 */
export function nodePresentation(node: TraceNode): NodePresentation {
  const rawKind = node.kind ?? "";
  const rawTitle = node.title ?? "";
  const detail = node.detail ?? {};
  const known = isTraceNodeKind(rawKind);
  const kind: TraceNodeKind = known ? rawKind : FALLBACK_NODE_KIND;
  const meta = presentations[kind];
  return {
    kind,
    rawKind,
    title: combineTitle(meta.label, rawTitle),
    rawTitle,
    icon: meta.icon,
    collapsible: meta.collapsible,
    structured: meta.structured,
    text: meta.structured ? null : readText(detail, "text"),
    detail,
  };
}

/* 将 ISO 时间格式化为中文本地时间（月-日 时:分）；缺失或非法时返回 null。 */
export function formatNodeTime(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }).format(date);
}

/*
 * 计算两个时间点之间的耗时并格式化：
 * 不足 1 秒按毫秒展示，不足 1 分钟按秒展示，其余按「时分秒」展示；
 * 任一时间缺失或非法、或结束早于开始时返回 null。
 */
export function formatDuration(startedAt: string | null | undefined, finishedAt: string | null | undefined): string | null {
  if (!startedAt || !finishedAt) {
    return null;
  }
  const start = Date.parse(startedAt);
  const end = Date.parse(finishedAt);
  if (Number.isNaN(start) || Number.isNaN(end) || end < start) {
    return null;
  }
  const ms = end - start;
  if (ms < 1000) {
    return `${ms} 毫秒`;
  }
  const totalSeconds = Math.floor(ms / 1000);
  if (totalSeconds < 60) {
    return ms % 1000 === 0 ? `${totalSeconds} 秒` : `${(ms / 1000).toFixed(1)} 秒`;
  }
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  const hours = Math.floor(minutes / 60);
  if (hours > 0) {
    const restMinutes = minutes % 60;
    return restMinutes > 0 ? `${hours} 小时 ${restMinutes} 分` : `${hours} 小时`;
  }
  return seconds > 0 ? `${minutes} 分 ${seconds} 秒` : `${minutes} 分`;
}
