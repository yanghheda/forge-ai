import { describe, expect, it } from "vitest";

import { parseAgentContext } from "./requirement-context";

describe("parseAgentContext", () => {
  it("解析需求详情页与交付阶段子路由", () => {
    expect(parseAgentContext("/requirements/31")).toEqual({ kind: "requirement", id: 31 });
    expect(parseAgentContext("/requirements/31/qa")).toEqual({ kind: "requirement", id: 31 });
    expect(parseAgentContext("/requirements/31/dev")).toEqual({ kind: "requirement", id: 31 });
  });

  it("解析缺陷详情页", () => {
    expect(parseAgentContext("/defects/7")).toEqual({ kind: "defect", id: 7 });
  });

  it("列表页与无关页面不返回上下文", () => {
    expect(parseAgentContext("/overview")).toBeUndefined();
    expect(parseAgentContext("/agent/command")).toBeUndefined();
    expect(parseAgentContext("/requirements")).toBeUndefined();
    expect(parseAgentContext("/defects")).toBeUndefined();
    expect(parseAgentContext("/requirements/abc")).toBeUndefined();
  });

  it("拒绝非法标识", () => {
    expect(parseAgentContext("/requirements/0")).toBeUndefined();
    expect(parseAgentContext("/requirements/-1")).toBeUndefined();
    expect(parseAgentContext("/defects/0")).toBeUndefined();
  });
});
