import type { ApiTransport } from "../transport";

/**
 * OpenAPI Client 生成流程的稳定接入点。
 * 会话 04 只固定 transport 注入边界；后续由 forge-server 的 /v3/api-docs 生成端点方法和 DTO。
 */
export class GeneratedApiClient {
  constructor(private readonly transport: ApiTransport) {}

  request<T = unknown>(path: string, init?: RequestInit): Promise<T> {
    return this.transport.request<T>(path, init);
  }
}
