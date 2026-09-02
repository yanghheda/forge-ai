import { GeneratedApiClient } from "./generated";
import { createApiTransport } from "./transport";

export { ApiError, isApiError } from "./api-error";
export { createApiTransport, resolveApiBaseUrl } from "./transport";

export const apiClient = new GeneratedApiClient(createApiTransport());
