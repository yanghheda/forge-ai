import { priorityLabel } from "@/lib/labels";
import type { RequirementBugBucket, RequirementPriorityBucket, RequirementStatusBucket } from "../api/work-item-api";
import { statusLabel } from "./requirement-detail-display";

/* 状态漏斗按需求生命周期推进顺序展示，终结状态排在末尾以便一眼看出流失去向。 */
export const funnelOrder = [
  "DRAFT",
  "REQUIREMENT_APPROVAL",
  "READY_FOR_REVIEW",
  "PRODUCT_REVIEW",
  "READY_FOR_UX",
  "UX_IN_PROGRESS",
  "UX_REVIEW",
  "READY_FOR_DEV",
  "IN_DEVELOPMENT",
  "READY_FOR_QA",
  "IN_QA",
  "IN_FIX_VERIFICATION",
  "IN_ACCEPTANCE",
  "READY_FOR_RELEASE",
  "RELEASED",
  "DONE",
  "REJECTED",
  "CANCELLED",
] as const;

/* 优先级按紧迫度降序排列，保证环形图扇区顺序稳定。 */
export const priorityOrder = ["URGENT", "HIGH", "MEDIUM", "LOW"] as const;

/* 缺陷严重级别的中文文案；历史数据未设置级别时归入「未分级」。 */
export const severityLabel: Record<string, string> = {
  BLOCKER: "阻断",
  CRITICAL: "致命",
  MAJOR: "严重",
  MINOR: "轻微",
  "": "未分级",
};

/* 缺陷生命周期状态的中文文案，与任务看板的状态代码保持一致。 */
export const bugStatusLabel: Record<string, string> = {
  OPEN: "待处理",
  IN_PROGRESS: "修复中",
  RESOLVED: "已修复",
  VERIFIED: "已验证",
  CLOSED: "已关闭",
  REOPENED: "重新打开",
  CANCELLED: "已取消",
};

/* 缺陷状态按「待处理到关闭」的推进顺序排列，保证堆叠图横轴稳定。 */
export const bugStatusOrder = ["TODO", "IN_PROGRESS", "BLOCKED", "WAITING_VERIFICATION", "DONE"] as const;

/* 严重级别降序，用于缺陷分布图例与堆叠顺序。 */
const severityOrder = ["BLOCKER", "CRITICAL", "MAJOR", "MINOR", ""];

export function funnelRows(buckets: RequirementStatusBucket[]) {
  const counts = new Map(buckets.map((bucket) => [bucket.status, bucket.total]));
  return funnelOrder.map((status) => ({ status, label: statusLabel[status] ?? status, total: counts.get(status) ?? 0 }));
}

export function priorityRows(buckets: RequirementPriorityBucket[]) {
  const counts = new Map(buckets.map((bucket) => [bucket.priority, bucket.total]));
  return priorityOrder.map((priority) => ({ priority, label: priorityLabel(priority), total: counts.get(priority) ?? 0 }));
}

export function bugRows(buckets: RequirementBugBucket[]) {
  return [...buckets].sort((left, right) => {
    const bySeverity = severityOrder.indexOf(left.severity) - severityOrder.indexOf(right.severity);
    return bySeverity === 0 ? left.status.localeCompare(right.status) : bySeverity;
  });
}

/* 后端用 null 表达「窗口内无样本」，展示层必须区分无样本与 0。 */
export function formatPercent(value: number | null) {
  return value === null ? "无样本" : `${Math.round(value * 100)}%`;
}

export function formatCycle(value: number | null) {
  return value === null ? "无样本" : `${value.toFixed(1)} 天`;
}

export function formatDelta(value: number | null) {
  if (value === null) return "上期无数据";
  const percent = Math.round(value * 100);
  return `${percent > 0 ? "+" : ""}${percent}% 较上期`;
}

export function formatDay(value: string) {
  return value.slice(5).replace("-", "/");
}
