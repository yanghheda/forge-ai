import { describe, expect, it } from "vitest";

import { parseRequirementId } from "./requirement-context";

describe("parseRequirementId", () => {
  it("解析需求详情页与交付阶段子路由", () => {
    expect(parseRequirementId("/requirements/31")).toBe(31);
    expect(parseRequirementId("/requirements/31/qa")).toBe(31);
    expect(parseRequirementId("/requirements/31/dev")).toBe(31);
  });

  it("非需求详情页不返回上下文", () => {
    expect(parseRequirementId("/overview")).toBeUndefined();
    expect(parseRequirementId("/agent/command")).toBeUndefined();
    expect(parseRequirementId("/requirements")).toBeUndefined();
    expect(parseRequirementId("/requirements/abc")).toBeUndefined();
  });

  it("拒绝非法标识", () => {
    expect(parseRequirementId("/requirements/0")).toBeUndefined();
    expect(parseRequirementId("/requirements/-1")).toBeUndefined();
  });
});
