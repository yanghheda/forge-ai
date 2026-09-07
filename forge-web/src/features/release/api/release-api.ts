import { apiClient } from "@/lib/api";

interface RequestClient {
  request<T>(path: string, init?: RequestInit): Promise<T>;
}

export interface PrecheckResult {
  rule: "WORK_ITEMS_READY" | "PIPELINE_GREEN" | "QA_PASSED" | "NO_BLOCKING_BUGS" | "ARTIFACTS_PRESENT" | "APPROVAL_POLICY";
  passed: boolean;
  details: string[];
}

export interface PrecheckSnapshot {
  id: number;
  status: "PASS" | "FAIL";
  checks: PrecheckResult[];
  resourceVersions: Record<string, number>;
  checkedByType: string;
  checkedById: number;
  checkedAt: string;
  current: boolean;
}

export interface ReleaseView {
  id: number;
  workspaceId: number;
  projectId: number;
  versionName: string;
  environment: string;
  status: "DRAFT" | "PRECHECKED" | "READY_FOR_APPROVAL" | "APPROVED" | "DEPLOYING" | "RELEASED" | "FAILED";
  releaseNote: string;
  itemIds: number[];
  latestPrecheck: PrecheckSnapshot | null;
  version: number;
}

export interface DeploymentView {
  id: number;
  releaseId: number;
  mode: "SIMULATED";
  status: "PENDING_APPROVAL" | "APPROVED" | "DEPLOYING" | "SUCCEEDED" | "FAILED" | "REJECTED" | "EXPIRED";
  requestedBy: number;
  approverUserId: number | null;
  approvalExpiresAt: string;
  resultCode: string | null;
  resultSummary: string | null;
  version: number;
}

const json = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

export const listReleases = (
  workspaceId: number,
  projectId: number,
  client: RequestClient = apiClient,
) => client.request<ReleaseView[]>(`/v1/releases?workspaceId=${workspaceId}&projectId=${projectId}`);

export const createRelease = (
  input: {
    workspaceId: number;
    projectId: number;
    versionName: string;
    environment: string;
    itemIds: number[];
    approvalTtlMinutes: number;
  },
  client: RequestClient = apiClient,
) => client.request<ReleaseView>("/v1/releases", json("POST", input));

export const updateReleaseNote = (
  input: { workspaceId: number; projectId: number; releaseId: number; note: string; expectedVersion: number },
  client: RequestClient = apiClient,
) => client.request<ReleaseView>(`/v1/releases/${input.releaseId}/note`, json("PUT", input));

export const runPrecheck = (
  input: { workspaceId: number; projectId: number; releaseId: number },
  client: RequestClient = apiClient,
) => client.request<PrecheckSnapshot>(`/v1/releases/${input.releaseId}/prechecks`, json("POST", input));

export const requestDeployment = (
  input: { workspaceId: number; projectId: number; releaseId: number; simulateFailure: boolean; idempotencyKey: string },
  client: RequestClient = apiClient,
) => client.request<DeploymentView>(
  `/v1/releases/${input.releaseId}/deployments`,
  json("POST", input),
);

export const listDeployments = (
  workspaceId: number,
  projectId: number,
  releaseId: number,
  client: RequestClient = apiClient,
) => client.request<DeploymentView[]>(
  `/v1/releases/${releaseId}/deployments?workspaceId=${workspaceId}&projectId=${projectId}`,
);

export const decideDeployment = (
  input: {
    workspaceId: number;
    projectId: number;
    deploymentId: number;
    decision: "APPROVE" | "REJECT";
    expectedVersion: number;
  },
  client: RequestClient = apiClient,
) => client.request<DeploymentView>(
  `/v1/releases/deployments/${input.deploymentId}:decide`,
  json("POST", input),
);
