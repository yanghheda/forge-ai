import { apiClient } from "@/lib/api";

interface RequestClient {
  request<T>(path: string, init?: RequestInit): Promise<T>;
}

export interface OrganizationRequirement {
  id: number;
  itemKey: string;
  title: string;
  description: string;
  status: string;
  priority: string;
  organizationName: string;
  reporterName: string;
  dueAt: string | null;
  goal: string;
  inScope: string;
  outOfScope: string;
  acceptanceCriteria: string[];
  businessValue: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface OrganizationRequirementPage {
  items: OrganizationRequirement[];
  page: number;
  pageSize: number;
  total: number;
}

export interface RequirementOverview {
  total: number;
  inProgress: number;
  completed: number;
}

export type RequirementRole = "PRODUCT" | "UX" | "DEVELOPER" | "QA";

export interface RequirementParticipant {
  role: RequirementRole;
  userId: number;
  displayName: string;
  email: string;
}

export interface RequirementMember {
  userId: number;
  displayName: string;
  email: string;
  roles: string[];
}

export interface RequirementDetails {
  workItemId: number;
  organizationId: number;
  goal: string;
  inScope: string;
  outOfScope: string;
  acceptanceCriteria: string[];
  businessValue: string;
  version: number;
  updatedAt: string | null;
}

const json = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

export const getRequirementOverview = (client: RequestClient = apiClient) => client.request<RequirementOverview>("/v1/requirements/overview");

export const listOrganizationRequirements = (filters: { mine?: boolean; q?: string; status?: string } = {}, client: RequestClient = apiClient) => {
  const query = new URLSearchParams();
  if (filters.mine) query.set("mine", "true");
  if (filters.q) query.set("q", filters.q);
  if (filters.status) query.set("status", filters.status);
  return client.request<OrganizationRequirementPage>(`/v1/requirements?${query.toString()}`);
};

export const createOrganizationRequirement = (input: { title: string; description: string; priority: string }, client: RequestClient = apiClient) => client.request<OrganizationRequirement>("/v1/requirements", json("POST", input));

export const getOrganizationRequirement = (id: number, client: RequestClient = apiClient) => client.request<OrganizationRequirement>(`/v1/requirements/${id}`);

export const getRequirementDetails = (id: number, client: RequestClient = apiClient) => client.request<RequirementDetails>(`/v1/work-items/${id}/details`);

export const saveRequirementDetails = (id: number, input: Omit<RequirementDetails, "workItemId" | "organizationId" | "updatedAt">, client: RequestClient = apiClient) => {
  const { version, ...details } = input;
  return client.request<RequirementDetails>(`/v1/work-items/${id}/details`, json("PUT", { ...details, expectedVersion: version }));
};

export const listRequirementMembers = (client: RequestClient = apiClient) => client.request<RequirementMember[]>("/v1/requirements/members");

export const getRequirementParticipants = (id: number, client: RequestClient = apiClient) => client.request<RequirementParticipant[]>(`/v1/requirements/${id}/participants`);

export const replaceRequirementParticipants = (id: number, participants: Array<{ role: RequirementRole; userId: number }>, client: RequestClient = apiClient) => client.request<RequirementParticipant[]>(`/v1/requirements/${id}/participants`, json("PUT", { participants }));

export type RequirementWorkflowAction = "SUBMIT_PRODUCT_REVIEW" | "APPROVE_PRODUCT_REVIEW" | "REJECT_PRODUCT_REVIEW" | "SUBMIT_UX_REVIEW" | "APPROVE_UX_REVIEW" | "REJECT_UX_REVIEW" | "SKIP_UX" | "SUBMIT_FOR_QA" | "START_QA" | "QA_PASS" | "QA_FAIL";

export interface RequirementWorkflow {
  version: number;
  availableActions: RequirementWorkflowAction[];
  guardHints: Partial<Record<RequirementWorkflowAction, string[]>>;
}

export const getRequirementWorkflow = (id: number, client: RequestClient = apiClient) => client.request<RequirementWorkflow>(`/v1/work-items/${id}`);

export const transitionRequirementWorkflow = (id: number, action: RequirementWorkflowAction, expectedVersion: number, client: RequestClient = apiClient) =>
  client.request(`/v1/work-items/${id}/transitions`, json("POST", { action, expectedVersion, idempotencyKey: crypto.randomUUID(), reason: null, checklist: null }));

export interface RequirementActivity {
  kind: "EVENT" | "COMMENT";
  id: number;
  actorId: number;
  action: RequirementWorkflowAction | null;
  reason: string | null;
  body: string | null;
  createdAt: string;
}

export const getRequirementActivity = (id: number, client: RequestClient = apiClient) => client.request<RequirementActivity[]>(`/v1/work-items/${id}/activity`);

export const createRequirementComment = (id: number, body: string, client: RequestClient = apiClient) => client.request<RequirementActivity>(`/v1/work-items/${id}/comments`, json("POST", { body }));
