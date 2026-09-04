import { apiClient, resolveApiBaseUrl } from "@/lib/api";

interface RequestClient {
  request<T>(path: string, init?: RequestInit): Promise<T>;
}

export interface AgentStep {
  stepNo: number;
  type: string;
  name: string;
  status: string;
  outputSummary: string | null;
  startedAt: string;
  finishedAt: string | null;
}

export interface AgentRunSnapshot {
  id: string;
  status: string;
  lastSequence: number;
  terminal: boolean;
  startedAt: string | null;
  finishedAt: string | null;
  errorCode: string | null;
  steps: AgentStep[];
}

export function getAgentRun(
  workspaceId: number,
  projectId: number,
  runId: string,
  client: RequestClient = apiClient,
): Promise<AgentRunSnapshot> {
  return client.request(
    `/v1/agent-runs/${runId}?workspaceId=${workspaceId}&projectId=${projectId}`,
  );
}

export function agentEventUrl(
  workspaceId: number,
  projectId: number,
  runId: string,
  afterSequence: number,
): string {
  const query = new URLSearchParams({
    workspaceId: String(workspaceId),
    projectId: String(projectId),
    afterSequence: String(afterSequence),
  });
  return `${resolveApiBaseUrl()}/v1/agent-runs/${runId}/events?${query}`;
}
