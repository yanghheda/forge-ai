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

export const listRequirementMembers = (client: RequestClient = apiClient) => client.request<RequirementMember[]>("/v1/requirements/members");

export const getRequirementParticipants = (id: number, client: RequestClient = apiClient) => client.request<RequirementParticipant[]>(`/v1/requirements/${id}/participants`);

export const replaceRequirementParticipants = (id: number, participants: Array<{ role: RequirementRole; userId: number }>, client: RequestClient = apiClient) => client.request<RequirementParticipant[]>(`/v1/requirements/${id}/participants`, json("PUT", { participants }));
