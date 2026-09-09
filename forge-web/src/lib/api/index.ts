import { GeneratedApiClient } from "./generated";
import { createApiTransport } from "./transport";

export { ApiError, formatRequestError, getUserErrorMessage, isApiError } from "./api-error";
export { createApiTransport, resolveApiBaseUrl } from "./transport";

export const apiClient = new GeneratedApiClient(createApiTransport());
