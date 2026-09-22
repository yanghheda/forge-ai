import type { RequirementRole } from "../api/work-item-api";

export const stages = [
  { title: "产品评审", statuses: ["DRAFT", "REQUIREMENT_APPROVAL", "READY_FOR_REVIEW", "PRODUCT_REVIEW"] },
  { title: "UX 设计", statuses: ["READY_FOR_UX", "UX_IN_PROGRESS"] },
  { title: "UX 评审", statuses: ["UX_REVIEW"] },
  { title: "开发", statuses: ["READY_FOR_DEV", "IN_DEVELOPMENT"] },
  { title: "QA 测试", statuses: ["READY_FOR_QA", "IN_QA", "IN_FIX_VERIFICATION"] },
  { title: "验收", statuses: ["IN_ACCEPTANCE"] },
  { title: "发布", statuses: ["READY_FOR_RELEASE", "RELEASED", "DONE"] },
] as const;

export const statusLabel: Record<string, string> = {
  DRAFT: "草稿",
  REQUIREMENT_APPROVAL: "需求待审核",
  READY_FOR_REVIEW: "待评审",
  PRODUCT_REVIEW: "评审中",
  READY_FOR_UX: "UX待设计",
  IN_FIX_VERIFICATION: "验证修复中",
  IN_ACCEPTANCE: "验收中",
  TODO: "待处理",
  IN_PROGRESS: "进行中",
  BLOCKED: "阻塞中",
  WAITING_VERIFICATION: "待验证",
  UX_IN_PROGRESS: "UX设计中",
  UX_REVIEW: "UX待审核",
  READY_FOR_DEV: "待开发",
  IN_DEVELOPMENT: "开发中",
  READY_FOR_QA: "待测试",
  IN_QA: "测试中",
  READY_FOR_RELEASE: "待发布",
  RELEASED: "发布完成",
  DONE: "已完成",
  REJECTED: "已拒绝",
  CANCELLED: "已取消",
};

export const roleMeta: Record<RequirementRole, { label: string; duty: string; agent: string }> = {
  PRODUCT: { label: "产品负责人", duty: "PRD 维护 · 产品评审批准", agent: "ProductAgent" },
  UX: { label: "UX负责人", duty: "UX Spec · 交互评审", agent: "UXAgent" },
  DEVELOPER: { label: "开发负责人", duty: "MR 评审 · 开发质量把关", agent: "DevAgent" },
  QA: { label: "测试负责人", duty: "测试计划 · 用例与验收", agent: "QaAgent" },
};

export function stageIndex(status: string) {
  const index = stages.findIndex((stage) => (stage.statuses as readonly string[]).includes(status));
  return index < 0 ? 0 : index;
}

export function formatDate(value?: string | null, includeTime = false) {
  if (!value) return "未设置";
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    ...(includeTime ? { hour: "2-digit", minute: "2-digit", hour12: false } : {}),
  })
    .format(new Date(value))
    .replaceAll("/", "-");
}
