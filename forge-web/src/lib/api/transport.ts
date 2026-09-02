import { ApiError } from "./api-error";

interface ErrorEnvelope {
  code?: unknown;
  message?: unknown;
  requestId?: unknown;
  details?: unknown;
}

export interface ApiTransportOptions {
  baseUrl?: string;
  fetchImplementation?: typeof fetch;
}

export interface ApiTransport {
  request<T = unknown>(path: string, init?: RequestInit): Promise<T>;
}

export function resolveApiBaseUrl(): string {
  const configuredUrl = process.env.NEXT_PUBLIC_API_BASE_URL?.trim();
  return configuredUrl ? configuredUrl.replace(/\/$/, "") : "/api";
}

function asErrorEnvelope(value: unknown): ErrorEnvelope | undefined {
  return typeof value === "object" && value !== null ? (value as ErrorEnvelope) : undefined;
}

function asDetails(value: unknown): Readonly<Record<string, unknown>> {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
}

async function readJson(response: Response): Promise<unknown> {
  if (!response.headers.get("content-type")?.includes("application/json")) {
    return undefined;
  }

  return response.json().catch(() => undefined);
}

export function createApiTransport({
  baseUrl = resolveApiBaseUrl(),
  fetchImplementation = fetch,
}: ApiTransportOptions = {}): ApiTransport {
  const normalizedBaseUrl = baseUrl.replace(/\/$/, "");

  return {
    async request<T = unknown>(path: string, init: RequestInit = {}): Promise<T> {
      const response = await fetchImplementation(`${normalizedBaseUrl}/${path.replace(/^\//, "")}`, {
        ...init,
        credentials: "include",
        headers: {
          Accept: "application/json",
          ...init.headers,
        },
      });
      const body = await readJson(response);

      if (!response.ok) {
        const envelope = asErrorEnvelope(body);
        throw new ApiError({
          code: typeof envelope?.code === "string" ? envelope.code : "HTTP_ERROR",
          message:
            typeof envelope?.message === "string"
              ? envelope.message
              : `Request failed with status ${response.status}`,
          requestId: typeof envelope?.requestId === "string" ? envelope.requestId : undefined,
          details: asDetails(envelope?.details),
          status: response.status,
        });
      }

      return body as T;
    },
  };
}
