import { describe, expect, it } from "vitest";

import { canEditModule, type EditableModule } from "./module-access";

describe("canEditModule", () => {
  it("只允许模块对应角色编辑", () => {
    const allowed: Array<[EditableModule, string]> = [
      ["prd", "PRODUCT"],
      ["ux", "UX"],
      ["development", "DEVELOPER"],
      ["qa", "QA"],
      ["release", "DEVELOPER"],
      ["release", "RELEASE_APPROVER"],
    ];
    for (const [module, role] of allowed) {
      expect(canEditModule([role], module)).toBe(true);
    }
  });

  it("其他角色与非对应模块一律只读", () => {
    expect(canEditModule(["QA"], "prd")).toBe(false);
    expect(canEditModule(["DEVELOPER"], "ux")).toBe(false);
    expect(canEditModule(["PRODUCT"], "qa")).toBe(false);
    expect(canEditModule(["QA"], "release")).toBe(false);
    expect(canEditModule(["UX"], "development")).toBe(false);
  });

  it("OWNER 与 ADMIN 不绕过模块限制", () => {
    expect(canEditModule(["OWNER", "ADMIN"], "prd")).toBe(false);
    expect(canEditModule(["OWNER", "ADMIN", "QA"], "qa")).toBe(true);
  });

  it("角色缺失时按只读处理", () => {
    expect(canEditModule(undefined, "prd")).toBe(false);
    expect(canEditModule([], "prd")).toBe(false);
  });
});
