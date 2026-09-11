import { apiClient } from "@/lib/api";

interface RequestClient {
  request<T = unknown>(path: string, init?: RequestInit): Promise<T>;
}

export interface PipelineRun {
  id: number;
  remotePipelineId: number;
  ref: string;
  commitSha: string;
  status: string;
  webUrl: string;
  startedAt: string | null;
  finishedAt: string | null;
  lastSyncedAt: string;
}

export interface GitLabConnection {
  id: number;
  organizationId: number;
  name: string;
  baseUrl: string;
  tokenFingerprint: string;
  status: "UNVERIFIED" | "ACTIVE" | "ERROR";
  lastTestedAt?: string;
  version: number;
}

export interface CreateConnectionInput {
  name: string;
  baseUrl: string;
  token: string;
}

export interface BindRepositoryInput {
  connectionId: number;
  remoteProjectId: string;
}

function jsonRequest(method: string, body: unknown): RequestInit {
  return {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  };
}

export function createConnection(input: CreateConnectionInput, client: RequestClient = apiClient): Promise<GitLabConnection> {
  return client.request("/v1/gitlab/connections", jsonRequest("POST", input));
}

export function listConnections(client: RequestClient = apiClient): Promise<GitLabConnection[]> {
  return client.request("/v1/gitlab/connections");
}

export function rotateToken(connectionId: number, expectedVersion: number, token: string, client: RequestClient = apiClient): Promise<GitLabConnection> {
  return client.request(`/v1/gitlab/connections/${connectionId}/token`, jsonRequest("PATCH", { expectedVersion, token }));
}

export function testConnection(connectionId: number, client: RequestClient = apiClient): Promise<{ externalUserId: string; username: string }> {
  return client.request(`/v1/gitlab/connections/${connectionId}/test`, {
    method: "POST",
  });
}

export function bindRepository(input: BindRepositoryInput, client: RequestClient = apiClient): Promise<unknown> {
  return client.request("/v1/gitlab/repositories/bind", jsonRequest("POST", input));
}

export function listPipelines(client: RequestClient = apiClient): Promise<PipelineRun[]> {
  return client.request("/v1/development/pipelines");
}

export function triggerPipeline(input: { ref: string }, client: RequestClient = apiClient): Promise<PipelineRun> {
  return client.request("/v1/development/pipelines", jsonRequest("POST", input));
}
