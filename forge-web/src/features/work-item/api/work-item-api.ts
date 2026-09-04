import { apiClient } from "@/lib/api";

interface RequestClient {
  request<T>(path: string, init?: RequestInit): Promise<T>;
}
export type WorkflowAction =
  | "SUBMIT_PRODUCT_REVIEW"
  | "APPROVE_PRODUCT_REVIEW"
  | "REJECT_PRODUCT_REVIEW"
  | "SUBMIT_UX_REVIEW"
  | "APPROVE_UX_REVIEW"
  | "REJECT_UX_REVIEW"
  | "START"
  | "SUBMIT_REVIEW"
  | "APPROVE"
  | "REJECT";
export interface WorkItem {
  id: number;
  workspaceId: number;
  projectId: number;
  itemKey: string;
  title: string;
  description: string;
  status: string;
  priority: string;
  version: number;
}
export interface WorkItemDetail extends WorkItem {
  availableActions: WorkflowAction[];
  guardHints: Partial<Record<WorkflowAction, string[]>>;
}
export interface WorkItemPage {
  items: WorkItem[];
  page: number;
  pageSize: number;
  total: number;
}
export interface RequirementDetails {
  workItemId: number;
  workspaceId: number;
  goal: string;
  inScope: string;
  outOfScope: string;
  acceptanceCriteria: string[];
  businessValue: string;
  version: number;
  updatedAt: string | null;
}
export interface WorkItemEvent {
  id: number;
  action: WorkflowAction;
  fromStatus: string;
  toStatus: string;
  actorId: number;
  reason: string | null;
  createdAt: string;
}
const json = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});
export const listRequirements = (
  workspaceId: number,
  projectId: number,
  client: RequestClient = apiClient,
) =>
  client.request<WorkItemPage>(
    `/v1/work-items?workspaceId=${workspaceId}&projectId=${projectId}&type=REQUIREMENT`,
  );
export const listUxTasks = (
  workspaceId: number,
  projectId: number,
  client: RequestClient = apiClient,
) =>
  client.request<WorkItemPage>(
    `/v1/work-items?workspaceId=${workspaceId}&projectId=${projectId}&type=UX_TASK`,
  );
export const createRequirement = (
  input: {
    workspaceId: number;
    projectId: number;
    title: string;
    description: string;
    priority: string;
  },
  client: RequestClient = apiClient,
) =>
  client.request<WorkItem>(
    "/v1/work-items",
    json("POST", { ...input, type: "REQUIREMENT" }),
  );
export const getWorkItem = (
  workspaceId: number,
  projectId: number,
  id: number,
  client: RequestClient = apiClient,
) =>
  client.request<WorkItemDetail>(
    `/v1/work-items/${id}?workspaceId=${workspaceId}&projectId=${projectId}`,
  );
export const getRequirementDetails = (
  workspaceId: number,
  projectId: number,
  id: number,
  client: RequestClient = apiClient,
) =>
  client.request<RequirementDetails>(
    `/v1/work-items/${id}/details?workspaceId=${workspaceId}&projectId=${projectId}`,
  );
export const saveRequirementDetails = (
  workspaceId: number,
  projectId: number,
  id: number,
  input: Omit<RequirementDetails, "workItemId" | "workspaceId" | "updatedAt">,
  client: RequestClient = apiClient,
) =>
  client.request<RequirementDetails>(
    `/v1/work-items/${id}/details?workspaceId=${workspaceId}&projectId=${projectId}`,
    json("PUT", input),
  );
export const transitionRequirement = (
  workspaceId: number,
  projectId: number,
  id: number,
  action: WorkflowAction,
  expectedVersion: number,
  reason?: string,
  checklist?: string[],
  client: RequestClient = apiClient,
) =>
  client.request(
    `/v1/work-items/${id}/transitions?workspaceId=${workspaceId}&projectId=${projectId}`,
    json("POST", {
      action,
      expectedVersion,
      reason,
      checklist,
      idempotencyKey: crypto.randomUUID(),
    }),
  );
export const getWorkItemEvents = (
  workspaceId: number,
  projectId: number,
  id: number,
  client: RequestClient = apiClient,
) =>
  client.request<WorkItemEvent[]>(
    `/v1/work-items/${id}/activity?workspaceId=${workspaceId}&projectId=${projectId}`,
  );
