export interface ApiErrorOptions {
  code: string;
  message: string;
  status: number;
  requestId?: string;
  details?: Readonly<Record<string, unknown>>;
}

export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly requestId?: string;
  readonly details: Readonly<Record<string, unknown>>;

  constructor({ code, message, status, requestId, details = {} }: ApiErrorOptions) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
    this.requestId = requestId;
    this.details = details;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}
