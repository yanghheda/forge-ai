import type { NotificationItem } from "../api/console-api";

/* 通知类型到中文标签的映射；未登记的类型统一回落为「通知」，避免后端新增类型时丢失展示。 */
const TYPE_LABELS: Record<string, string> = {
  APPROVAL_REQUESTED: "待审批",
  APPROVAL_DECIDED: "审批结果",
  DEPLOYMENT_APPROVAL_REQUESTED: "部署待审批",
  DEPLOYMENT_APPROVAL_DECIDED: "部署审批结果",
  WORK_ITEM_ASSIGNED: "新指派",
  REVIEW_REQUESTED: "评审待办",
  REQUIREMENT_HANDOFF: "状态流转",
};

/* 通知类型的中文展示标签；未登记的类型返回兜底文案「通知」。 */
export function notificationTypeLabel(type: string): string {
  return TYPE_LABELS[type] ?? "通知";
}

/* 将资源 ID 归一为可跳转的正整数主键；缺失、空串、非数字、0 与负数均视为无效。 */
function positiveInteger(value: string | null): number | null {
  if (value === null || !/^\d+$/.test(value)) {
    return null;
  }
  const parsed = Number(value);
  return parsed > 0 ? parsed : null;
}

/* 按资源类型映射抽屉点击后的跳转路径；无法映射时返回 null，此时仅标记已读而不跳转。 */
export function notificationLink(item: Pick<NotificationItem, "resourceType" | "resourceId">): string | null {
  const { resourceType, resourceId } = item;
  // APPROVAL 与 AGENT_RUN 的目标是固定入口，不依赖具体资源 ID。
  if (resourceType === "APPROVAL") {
    return "/approvals";
  }
  if (resourceType === "AGENT_RUN") {
    return "/agent/trace";
  }
  const id = positiveInteger(resourceId);
  if (id === null) {
    return null;
  }
  switch (resourceType) {
    case "REQUIREMENT":
      return `/requirements/${id}`;
    case "REQUIREMENT_UX":
      return `/requirements/${id}/ux`;
    case "REQUIREMENT_DEVELOPMENT":
      return `/requirements/${id}/development`;
    case "REQUIREMENT_QA":
      return `/requirements/${id}/qa`;
    case "REQUIREMENT_RELEASE":
      return `/requirements/${id}/release`;
    case "DEFECT":
      return `/defects/${id}`;
    default:
      return null;
  }
}
