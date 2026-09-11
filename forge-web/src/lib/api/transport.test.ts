import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "./api-error";
import { createApiTransport, resolveApiBaseUrl } from "./transport";

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("resolveApiBaseUrl", () => {
  it("默认使用同源 API 前缀，并清理配置末尾斜线", () => {
    expect(resolveApiBaseUrl()).toBe("/api");

    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "http://localhost:8080/api/");
    expect(resolveApiBaseUrl()).toBe("http://localhost:8080/api");
  });
});

describe("ApiTransport", () => {
  it("携带 Cookie credentials 并解析成功响应", async () => {
    const fetchImplementation = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ code: 0, message: "success", data: { status: "UP" } }), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    const transport = createApiTransport({
      baseUrl: "http://localhost:8080/api",
      fetchImplementation,
    });

    await expect(transport.request("/v1/system/status")).resolves.toEqual({ status: "UP" });
    expect(fetchImplementation).toHaveBeenCalledWith("http://localhost:8080/api/v1/system/status", expect.objectContaining({ credentials: "include" }));
  });

  it("成功信封只在 transport 解包一次", async () => {
    const fetchImplementation = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ code: 0, message: "success", data: { code: 0 } }), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    const transport = createApiTransport({ baseUrl: "/api", fetchImplementation });

    await expect(transport.request("/v1/example")).resolves.toEqual({ code: 0 });
  });

  it("将后端错误信封映射为包含 requestId 的 ApiError", async () => {
    const fetchImplementation = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          code: "RESOURCE_NOT_FOUND",
          message: "Project was not found",
          requestId: "req_01TEST",
          details: { resource: "project" },
        }),
        { status: 404, headers: { "content-type": "application/json" } },
      ),
    );
    const transport = createApiTransport({ baseUrl: "/api", fetchImplementation });

    const error = await transport.request("/v1/projects/missing").catch((reason: unknown) => reason);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      code: "RESOURCE_NOT_FOUND",
      requestId: "req_01TEST",
      status: 404,
      details: { resource: "project" },
    });
  });

  it("非 JSON 错误不会泄露响应正文", async () => {
    const fetchImplementation = vi.fn<typeof fetch>().mockResolvedValue(
      new Response("internal stack trace", {
        status: 502,
        headers: { "content-type": "text/plain" },
      }),
    );
    const transport = createApiTransport({ baseUrl: "/api", fetchImplementation });

    const error = await transport.request("/v1/system/status").catch((reason: unknown) => reason);

    expect(error).toMatchObject({
      code: "HTTP_ERROR",
      message: "Request failed with status 502",
      status: 502,
    });
    expect(String(error)).not.toContain("stack trace");
  });

  it("修改请求先获取 CSRF Token 并通过 Header 携带", async () => {
    const fetchImplementation = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            code: 0,
            message: "success",
            data: { headerName: "X-CSRF-TOKEN", token: "csrf-test-token" },
          }),
          {
            status: 200,
            headers: { "content-type": "application/json" },
          },
        ),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    const transport = createApiTransport({ baseUrl: "/api", fetchImplementation });

    await transport.request("/v1/auth/login", { method: "POST", body: "{}" });

    expect(fetchImplementation).toHaveBeenNthCalledWith(1, "/api/v1/auth/csrf", expect.objectContaining({ credentials: "include" }));
    expect(fetchImplementation).toHaveBeenNthCalledWith(
      2,
      "/api/v1/auth/login",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ "X-CSRF-TOKEN": "csrf-test-token" }),
      }),
    );
  });

  it("CSRF 拒绝时刷新 Token 并只重试一次", async () => {
    const jsonHeaders = { "content-type": "application/json" };
    const fetchImplementation = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            code: 0,
            message: "success",
            data: { headerName: "X-CSRF-TOKEN", token: "old" },
          }),
          { status: 200, headers: jsonHeaders },
        ),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ code: "CSRF_REJECTED" }), {
          status: 403,
          headers: jsonHeaders,
        }),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            code: 0,
            message: "success",
            data: { headerName: "X-CSRF-TOKEN", token: "new" },
          }),
          { status: 200, headers: jsonHeaders },
        ),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    const transport = createApiTransport({ baseUrl: "/api", fetchImplementation });

    await expect(transport.request("/v1/auth/logout", { method: "POST" })).resolves.toBeUndefined();
    expect(fetchImplementation).toHaveBeenCalledTimes(4);
    expect(fetchImplementation.mock.calls[3]?.[1]?.headers).toEqual(expect.objectContaining({ "X-CSRF-TOKEN": "new" }));
  });

  it("安全读取和普通 403 均不触发 CSRF 刷新", async () => {
    const fetchImplementation = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ code: "FORBIDDEN", message: "Forbidden" }), {
        status: 403,
        headers: { "content-type": "application/json" },
      }),
    );
    const transport = createApiTransport({ baseUrl: "/api", fetchImplementation });

    await expect(transport.request("/v1/me")).rejects.toMatchObject({ code: "FORBIDDEN" });
    expect(fetchImplementation).toHaveBeenCalledTimes(1);
  });
});
