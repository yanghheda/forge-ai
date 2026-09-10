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
  logo: File | null;
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

export async function initializeInstance(input: InitializeInput, client: RequestClient = apiClient): Promise<unknown> {
  if (!input.logo) throw new Error("请选择公司 Logo");
  const { logo, ...fields } = input;
  const logoBase64 = await fileToBase64(logo);
  return client.request("/v1/setup/initialize", jsonRequest("POST", {
    ...fields, logoFileName: logo.name, logoMediaType: logo.type, logoBase64,
  }));
}

function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("无法读取公司 Logo"));
    reader.onload = () => resolve(String(reader.result).split(",", 2)[1] ?? "");
    reader.readAsDataURL(file);
  });
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
