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
      new Response(JSON.stringify({ status: "UP" }), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    const transport = createApiTransport({
      baseUrl: "http://localhost:8080/api",
      fetchImplementation,
    });

    await expect(transport.request("/v1/system/status")).resolves.toEqual({ status: "UP" });
    expect(fetchImplementation).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/system/status",
      expect.objectContaining({ credentials: "include" }),
    );
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
});
