import { apiClient } from "@/lib/api";

interface RequestClient {
  request<T = unknown>(path: string, init?: RequestInit): Promise<T>;
}

export interface Project {
  id: number;
  workspaceId: number;
  key: string;
  name: string;
  description: string;
  status: "ACTIVE" | "ARCHIVED";
  archivedAt: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectMember {
  id: number;
  workspaceId: number;
  projectId: number;
  userId: number;
  email: string;
  displayName: string;
  active: boolean;
}

export interface CreateProjectInput {
  workspaceId: number;
  key: string;
  name: string;
  description: string;
}

export interface ArchiveProjectInput {
  workspaceId: number;
  projectId: number;
  expectedVersion: number;
}

function jsonRequest(method: string, body?: unknown): RequestInit {
  return {
    method,
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  };
}

export function listProjects(workspaceId: number, client: RequestClient = apiClient): Promise<Project[]> {
  return client.request(`/v1/projects?workspaceId=${workspaceId}`);
}

export function getProject(
  workspaceId: number,
  projectId: number,
  client: RequestClient = apiClient,
): Promise<Project> {
  return client.request(`/v1/projects/${projectId}?workspaceId=${workspaceId}`);
}

export function createProject(input: CreateProjectInput, client: RequestClient = apiClient): Promise<Project> {
  return client.request("/v1/projects", jsonRequest("POST", input));
}

export function archiveProject(input: ArchiveProjectInput, client: RequestClient = apiClient): Promise<void> {
  return client.request(
    `/v1/projects/${input.projectId}?workspaceId=${input.workspaceId}`,
    jsonRequest("PATCH", { expectedVersion: input.expectedVersion }),
  );
}

export function listProjectMembers(
  workspaceId: number,
  projectId: number,
  client: RequestClient = apiClient,
): Promise<ProjectMember[]> {
  return client.request(`/v1/projects/${projectId}/members?workspaceId=${workspaceId}`);
}

export function addProjectMember(
  workspaceId: number,
  projectId: number,
  email: string,
  client: RequestClient = apiClient,
): Promise<void> {
  return client.request(`/v1/projects/${projectId}/members?workspaceId=${workspaceId}`, jsonRequest("POST", { email }));
}

export function removeProjectMember(
  workspaceId: number,
  projectId: number,
  userId: number,
  client: RequestClient = apiClient,
): Promise<void> {
  return client.request(`/v1/projects/${projectId}/members/${userId}?workspaceId=${workspaceId}`, { method: "DELETE" });
}
