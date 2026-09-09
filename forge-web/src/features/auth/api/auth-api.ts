import { apiClient } from "@/lib/api";

interface RequestClient {
  request<T = unknown>(path: string, init?: RequestInit): Promise<T>;
}

export interface SetupStatus {
  initialized: boolean;
}

export interface InitializeInput {
  adminEmail: string;
  adminDisplayName: string;
  password: string;
  organizationName: string;
  organizationSlug: string;
}

export interface RegisterInput {
  email: string;
  displayName: string;
  password: string;
  role: "PRODUCT" | "UX" | "DEVELOPER" | "QA";
}

export interface LoginInput {
  email: string;
  password: string;
}

export interface WorkspaceAccess {
  id: number;
  slug: string;
  name: string;
  roles: string[];
}

export interface CurrentUser {
  id: number;
  email: string;
  displayName: string;
  workspaces: WorkspaceAccess[];
}

function jsonRequest(method: string, body?: unknown): RequestInit {
  return {
    method,
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  };
}

export function getSetupStatus(client: RequestClient = apiClient): Promise<SetupStatus> {
  return client.request("/v1/setup/status");
}

export function initializeInstance(input: InitializeInput, client: RequestClient = apiClient): Promise<unknown> {
  return client.request("/v1/setup/initialize", jsonRequest("POST", input));
}

export function login(input: LoginInput, client: RequestClient = apiClient): Promise<void> {
  return client.request("/v1/auth/login", jsonRequest("POST", input));
}

export function register(input: RegisterInput, client: RequestClient = apiClient): Promise<{ userId: number }> {
  return client.request("/v1/auth/register", jsonRequest("POST", input));
}

export function getCurrentUser(client: RequestClient = apiClient): Promise<CurrentUser> {
  return client.request("/v1/me");
}

export function logout(client: RequestClient = apiClient): Promise<void> {
  return client.request("/v1/auth/logout", { method: "POST" });
}
