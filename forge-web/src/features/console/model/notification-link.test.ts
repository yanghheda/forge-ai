import { describe, expect, it } from "vitest";

import { notificationLink, notificationTypeLabel } from "./notification-link";

describe("notificationLink", () => {
  it("APPROVAL 与 AGENT_RUN 跳到固定入口且忽略资源 ID", () => {
    expect(notificationLink({ resourceType: "APPROVAL", resourceId: "9" })).toBe("/approvals");
    expect(notificationLink({ resourceType: "APPROVAL", resourceId: null })).toBe("/approvals");
    expect(notificationLink({ resourceType: "AGENT_RUN", resourceId: "run-1" })).toBe("/agent/trace");
    expect(notificationLink({ resourceType: "AGENT_RUN", resourceId: null })).toBe("/agent/trace");
  });

  it("按需求与缺陷资源类型拼出深链路径", () => {
    expect(notificationLink({ resourceType: "REQUIREMENT", resourceId: "12" })).toBe("/requirements/12");
    expect(notificationLink({ resourceType: "REQUIREMENT_UX", resourceId: "12" })).toBe("/requirements/12/ux");
    expect(notificationLink({ resourceType: "REQUIREMENT_DEVELOPMENT", resourceId: "12" })).toBe("/requirements/12/development");
    expect(notificationLink({ resourceType: "REQUIREMENT_QA", resourceId: "12" })).toBe("/requirements/12/qa");
    expect(notificationLink({ resourceType: "REQUIREMENT_RELEASE", resourceId: "12" })).toBe("/requirements/12/release");
    expect(notificationLink({ resourceType: "DEFECT", resourceId: "34" })).toBe("/defects/34");
  });

  it("未知资源类型或空资源类型返回 null", () => {
    expect(notificationLink({ resourceType: "UNKNOWN", resourceId: "12" })).toBeNull();
    expect(notificationLink({ resourceType: null, resourceId: "12" })).toBeNull();
  });

  it("非法资源 ID 返回 null", () => {
    expect(notificationLink({ resourceType: "REQUIREMENT", resourceId: null })).toBeNull();
    expect(notificationLink({ resourceType: "REQUIREMENT", resourceId: "" })).toBeNull();
    expect(notificationLink({ resourceType: "REQUIREMENT", resourceId: "abc" })).toBeNull();
    expect(notificationLink({ resourceType: "REQUIREMENT", resourceId: "0" })).toBeNull();
    expect(notificationLink({ resourceType: "DEFECT", resourceId: "-3" })).toBeNull();
  });
});

describe("notificationTypeLabel", () => {
  it("映射全部已知通知类型", () => {
    expect(notificationTypeLabel("APPROVAL_REQUESTED")).toBe("待审批");
    expect(notificationTypeLabel("APPROVAL_DECIDED")).toBe("审批结果");
    expect(notificationTypeLabel("DEPLOYMENT_APPROVAL_REQUESTED")).toBe("部署待审批");
    expect(notificationTypeLabel("DEPLOYMENT_APPROVAL_DECIDED")).toBe("部署审批结果");
    expect(notificationTypeLabel("WORK_ITEM_ASSIGNED")).toBe("新指派");
    expect(notificationTypeLabel("REVIEW_REQUESTED")).toBe("评审待办");
    expect(notificationTypeLabel("REQUIREMENT_HANDOFF")).toBe("状态流转");
  });

  it("未知类型回落为「通知」", () => {
    expect(notificationTypeLabel("SOMETHING_ELSE")).toBe("通知");
    expect(notificationTypeLabel("")).toBe("通知");
  });
});
