import { apiClient } from "@/lib/api";

interface RequestClient {
  request<T = unknown>(path: string, init?: RequestInit): Promise<T>;
}

export interface WorkspaceMember {
  id: number;
  workspaceId: number;
  userId: number;
  email: string;
  displayName: string;
  active: boolean;
}

function jsonRequest(body: unknown): RequestInit {
  return { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) };
}

export function listWorkspaceMembers(workspaceId: number, client: RequestClient = apiClient): Promise<WorkspaceMember[]> {
  return client.request(`/v1/workspaces/${workspaceId}/members`);
}

export function addWorkspaceMember(workspaceId: number, email: string, role: string, client: RequestClient = apiClient): Promise<void> {
  return client.request(`/v1/workspaces/${workspaceId}/members`, jsonRequest({ email, role }));
}

export interface CreateWorkspaceMemberInput {
  email: string;
  displayName: string;
  password: string;
  role: string;
}

export function createWorkspaceMember(workspaceId: number, input: CreateWorkspaceMemberInput, client: RequestClient = apiClient): Promise<void> {
  return client.request(`/v1/workspaces/${workspaceId}/members/register`, jsonRequest(input));
}

export function removeWorkspaceMember(workspaceId: number, userId: number, client: RequestClient = apiClient): Promise<void> {
  return client.request(`/v1/workspaces/${workspaceId}/members/${userId}`, { method: "DELETE" });
}
