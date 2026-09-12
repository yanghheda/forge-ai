import type { RequirementRole, RequirementWorkflowAction } from "../api/work-item-api";

export const stages = [
  { title: "产品评审", statuses: ["DRAFT", "PRODUCT_REVIEW"] },
  { title: "UX 设计", statuses: ["UX_IN_PROGRESS"] },
  { title: "UX 评审", statuses: ["UX_REVIEW"] },
  { title: "开发", statuses: ["READY_FOR_DEV", "IN_DEVELOPMENT"] },
  { title: "QA 测试", statuses: ["READY_FOR_QA", "IN_QA"] },
  { title: "发布", statuses: ["READY_FOR_RELEASE", "RELEASED", "DONE"] },
] as const;

export const statusLabel: Record<string, string> = {
  DRAFT: "草稿",
  PRODUCT_REVIEW: "产品评审中",
  UX_IN_PROGRESS: "UX 设计中",
  UX_REVIEW: "UX 评审中",
  READY_FOR_DEV: "待开发",
  IN_DEVELOPMENT: "开发中",
  READY_FOR_QA: "待测试",
  IN_QA: "测试中",
  READY_FOR_RELEASE: "待发布",
  RELEASED: "已发布",
  DONE: "已完成",
  REJECTED: "已拒绝",
  CANCELLED: "已取消",
};

export const roleMeta: Record<RequirementRole, { label: string; duty: string; agent: string }> = {
  PRODUCT: { label: "产品负责人", duty: "PRD 维护 · 产品评审批准", agent: "ProductAgent" },
  UX: { label: "UX 设计师", duty: "UX Spec · 交互评审", agent: "UXAgent" },
  DEVELOPER: { label: "开发负责人", duty: "MR 评审 · 开发质量把关", agent: "DevAgent" },
  QA: { label: "测试负责人", duty: "测试计划 · 用例与验收", agent: "QaAgent" },
};

export const actionLabel: Partial<Record<RequirementWorkflowAction, string>> = {
  SUBMIT_PRODUCT_REVIEW: "提交产品评审",
  APPROVE_PRODUCT_REVIEW: "通过产品评审",
  SUBMIT_UX_REVIEW: "提交 UX 评审",
  APPROVE_UX_REVIEW: "通过 UX 评审",
  SUBMIT_FOR_QA: "提交 QA",
  START_QA: "开始 QA",
  QA_PASS: "QA 通过",
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
