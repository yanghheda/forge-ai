import { apiClient } from "@/lib/api";

interface RequestClient {
  request<T>(path: string, init?: RequestInit): Promise<T>;
}

export interface CompanyMember {
  userId: number;
  displayName: string;
  email: string;
  status: "PENDING" | "ACTIVE" | "DISABLED";
  roles: string[];
  lastLoginAt: string | null;
  version: number;
}

export interface BoardItem {
  id: number;
  itemKey: string;
  title: string;
  type: string;
  lane: string;
  position: number;
  assigneeUserId: number | null;
  version: number;
}

export interface AgentConversation {
  id: number;
  title: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}
export interface AgentMessage {
  sender: "USER" | "AGENT";
  body: string;
  runId: string | null;
  createdAt: string;
}
export interface SearchResult {
  type: string;
  id: string;
  title: string;
  subtitle: string;
}
export interface NotificationItem {
  id: number;
  type: string;
  title: string;
  body: string;
  resourceType: string | null;
  resourceId: string | null;
  readAt: string | null;
  createdAt: string;
}
export interface DashboardOverview {
  weeklyDeliveries: number;
  activeAgents: number;
  personalTodos: number;
  stageDistribution: Record<string, number>;
  deliveryTrend: { date: string; value: number }[];
}
export interface AgentRunCreated {
  id: string;
  status: string;
}

const json = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

export const listCompanyMembers = (client: RequestClient = apiClient) => client.request<CompanyMember[]>("/v1/members");

export const updateCompanyMember = (userId: number, input: { displayName: string; status: "ACTIVE" | "DISABLED"; roles: string[]; expectedVersion: number }, client: RequestClient = apiClient) => client.request<CompanyMember>(`/v1/members/${userId}`, json("PATCH", input));

export const listBoardItems = (client: RequestClient = apiClient) => client.request<BoardItem[]>("/v1/task-board");

export const moveBoardItem = (id: number, lane: string, position: number, client: RequestClient = apiClient) => client.request<void>(`/v1/task-board/items/${id}/position`, json("PUT", { lane, position }));

export const searchConsole = (query: string, client: RequestClient = apiClient) => client.request<SearchResult[]>(`/v1/search?q=${encodeURIComponent(query)}`);

export const listNotifications = (client: RequestClient = apiClient) => client.request<NotificationItem[]>("/v1/notifications?limit=30");

export const getUnreadNotificationCount = (client: RequestClient = apiClient) => client.request<{ count: number }>("/v1/notifications/unread-count");

export const markNotificationRead = (id: number, client: RequestClient = apiClient) => client.request<void>(`/v1/notifications/${id}/read`, { method: "POST" });

export const getDashboardOverview = (client: RequestClient = apiClient) => client.request<DashboardOverview>("/v1/dashboard/overview");

export const listAgentConversations = (client: RequestClient = apiClient) => client.request<AgentConversation[]>("/v1/agent-conversations");

export const createAgentConversation = (title: string, client: RequestClient = apiClient) => client.request<AgentConversation>("/v1/agent-conversations", json("POST", { title }));

export const listAgentMessages = (id: number, client: RequestClient = apiClient) => client.request<AgentMessage[]>(`/v1/agent-conversations/${id}/messages`);

export const sendAgentMessage = (id: number, message: string, client: RequestClient = apiClient) => client.request<AgentRunCreated>(`/v1/agent-conversations/${id}/messages`, json("POST", { message, skill: "PRODUCT" }));

export const traceExportUrl = (runId: string) => `/api/v1/agent-runs/${encodeURIComponent(runId)}/export`;
