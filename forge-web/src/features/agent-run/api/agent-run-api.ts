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

export interface ApprovalSnapshot {
  id: string;
  runId: string;
  toolCallId: string;
  toolName: string;
  toolVersion: number;
  riskLevel: string;
  status: "PENDING" | "APPROVED" | "REJECTED" | "EXPIRED" | "CANCELLED";
  requestedBy: number;
  approverUserId: number | null;
  argumentHash: string;
  resources: Array<{ type: string; id: string; version: number }>;
  reason: string;
  expiresAt: string;
  version: number;
}

export function getAgentRun(
  organizationId: number,
  runId: string,
  client: RequestClient = apiClient,
): Promise<AgentRunSnapshot> {
  return client.request(
    `/v1/agent-runs/${runId}?organizationId=${organizationId}`,
  );
}

export function getRunApproval(
  organizationId: number,
  runId: string,
  client: RequestClient = apiClient,
): Promise<ApprovalSnapshot> {
  return client.request(
    `/v1/approvals/by-run/${runId}?organizationId=${organizationId}`,
  );
}

export function decideApproval(
  approval: ApprovalSnapshot,
  organizationId: number,
  decision: "APPROVE" | "REJECT",
  client: RequestClient = apiClient,
): Promise<ApprovalSnapshot> {
  return client.request(`/v1/approvals/${approval.id}:decide`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      organizationId,
      decision,
      expectedVersion: approval.version,
    }),
  });
}

export function agentEventUrl(
  organizationId: number,
  runId: string,
  afterSequence: number,
): string {
  const query = new URLSearchParams({
    organizationId: String(organizationId),
    afterSequence: String(afterSequence),
  });
  return `${resolveApiBaseUrl()}/v1/agent-runs/${runId}/events?${query}`;
}
