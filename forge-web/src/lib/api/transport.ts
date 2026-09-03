import { ApiError } from "./api-error";

interface ErrorEnvelope {
  code?: unknown;
  message?: unknown;
  requestId?: unknown;
  details?: unknown;
}

interface CsrfTokenResponse {
  headerName: string;
  token: string;
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
  let csrfToken: CsrfTokenResponse | undefined;
  let csrfRequest: Promise<CsrfTokenResponse> | undefined;

  async function fetchCsrfToken(): Promise<CsrfTokenResponse> {
    if (csrfToken) {
      return csrfToken;
    }
    csrfRequest ??= fetchImplementation(`${normalizedBaseUrl}/v1/auth/csrf`, {
      credentials: "include",
      headers: { Accept: "application/json" },
    })
      .then(async (response) => {
        const body = (await readJson(response)) as Partial<CsrfTokenResponse> | undefined;
        if (!response.ok || typeof body?.headerName !== "string" || typeof body.token !== "string") {
          throw new ApiError({
            code: "CSRF_TOKEN_UNAVAILABLE",
            message: "Unable to establish request security context",
            status: response.status,
          });
        }
        csrfToken = { headerName: body.headerName, token: body.token };
        return csrfToken;
      })
      .finally(() => {
        csrfRequest = undefined;
      });
    return csrfRequest;
  }

  function isStateChanging(method: string | undefined): boolean {
    return !["GET", "HEAD", "OPTIONS"].includes((method ?? "GET").toUpperCase());
  }

  async function perform<T>(path: string, init: RequestInit, retriedAfterCsrfFailure: boolean): Promise<T> {
    const headers: Record<string, string> = { Accept: "application/json" };
    new Headers(init.headers).forEach((value, key) => {
      headers[key] = value;
    });
    if (isStateChanging(init.method)) {
      const token = await fetchCsrfToken();
      headers[token.headerName] = token.token;
    }

    const response = await fetchImplementation(`${normalizedBaseUrl}/${path.replace(/^\//, "")}`, {
      ...init,
      credentials: "include",
      headers,
    });
    const body = await readJson(response);

    if (!response.ok) {
      const envelope = asErrorEnvelope(body);
      if (envelope?.code === "CSRF_REJECTED" && !retriedAfterCsrfFailure && isStateChanging(init.method)) {
        csrfToken = undefined;
        return perform<T>(path, init, true);
      }
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

    if (path.replace(/^\//, "") === "v1/auth/logout") {
      csrfToken = undefined;
    }
    return body as T;
  }

  return {
    request<T = unknown>(path: string, init: RequestInit = {}): Promise<T> {
      return perform<T>(path, init, false);
    },
  };
}
