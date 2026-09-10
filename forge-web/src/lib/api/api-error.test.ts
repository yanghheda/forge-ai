import { describe, expect, it } from "vitest";

import { ApiError, formatRequestError, getUserErrorMessage } from "./api-error";

describe("API 错误中文提示", () => {
  it.each([
    ["RESOURCE_NOT_FOUND", 404, "请求的资源不存在或当前账户无权访问。"],
    ["VERSION_CONFLICT", 409, "数据已被其他操作更新，请刷新后重试。"],
    ["WORKFLOW_GUARD_FAILED", 422, "当前流程条件尚未满足，请检查缺失项。"],
    ["GITLAB_TIMEOUT", 503, "GitLab 请求超时，请稍后重试。"],
    ["INTERNAL_ERROR", 500, "服务器处理请求时发生错误，请稍后重试。"],
  ])("将 %s 映射为中文", (code, status, expected) => {
    const error = new ApiError({ code, message: "Backend English message", status });

    expect(getUserErrorMessage(error)).toBe(expected);
  });

  it("支持后端将细分业务错误码放在 message 中的响应", () => {
    const error = new ApiError({
      code: "VALIDATION_FAILED",
      message: "SELF_APPROVAL_FORBIDDEN",
      status: 403,
    });

    expect(getUserErrorMessage(error)).toBe("不能审批自己发起的操作。");
  });

  it("未知错误按 HTTP 状态返回中文兜底且不透传响应消息", () => {
    const error = new ApiError({ code: "NEW_SERVER_ERROR", message: "Unknown backend failure", status: 503 });

    expect(getUserErrorMessage(error)).toBe("服务暂时不可用，请稍后重试。");
    expect(getUserErrorMessage(error)).not.toContain("Unknown backend failure");
  });

  it("面向用户的错误提示不展示请求编号", () => {
    const error = new ApiError({ code: "VERSION_CONFLICT", message: "Conflict", requestId: "req_123", status: 409 });

    expect(formatRequestError(error)).toBe("数据已被其他操作更新，请刷新后重试。");
    expect(formatRequestError(error)).not.toContain("req_123");
  });
});
