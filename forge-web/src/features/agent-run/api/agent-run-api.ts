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
  skill: "PRODUCT" | "UX" | "DEVELOPER" | "QA" | "RELEASE";
  workItemId: number | null;
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

export interface PendingApproval {
  /* 审批 ULID 标识 */
  id: string;
  /* 被冻结的 Tool 名称 */
  toolName: string;
  /* 风险等级，当前为 HIGH 或 MEDIUM */
  riskLevel: string;
  /* 发起人用户 ID */
  requestedBy: number;
  /* 发起人显示名 */
  requestedByName: string;
  /* 冻结时记录的脱敏原因 */
  reason: string;
  /* 审批有效期（ISO 字符串） */
  expiresAt: string;
  /* 乐观锁版本 */
  version: number;
}

export interface ConversationRun {
  /* Agent Run 的稳定业务 ID，用于拉取轨迹与关联事件 */
  runId: string;
  /* 该轮执行所属 Skill 角色 */
  skill: "PRODUCT" | "UX" | "DEVELOPER" | "QA" | "RELEASE";
  /* 关联工作项 ID，未绑定需求时为 null */
  workItemId: number | null;
  /* Run 当前状态机取值 */
  status: string;
  /* 是否已进入终态，终态后不再产生新事件 */
  terminal: boolean;
  /* 开始执行的 ISO 时间，尚未开始为 null */
  startedAt: string | null;
  /* 结束执行的 ISO 时间，未结束为 null */
  finishedAt: string | null;
  /* 失败时的机器可读错误码，成功或尚未失败为 null */
  errorCode: string | null;
  /* 已持久化的最大事件序号，作为 SSE 增量游标 */
  lastSequence: number;
  /* 该轮用户指令原文 */
  triggerMessage: string | null;
  /* 该轮 Agent 回答，未产生时为 null */
  answer: string | null;
}

export interface TraceNode {
  /* 稳定节点键，例如 seq:12 */
  key: string;
  /* 节点类型；后端可能新增类型，前端必须按未知类型兜底 */
  kind: string;
  /* 节点标题 */
  title: string;
  /* 节点状态，无状态概念时为 null */
  status: string | null;
  /* 关联事件序号，聚合节点可能为 null */
  sequence: number | null;
  /* 节点开始时间，无则为 null */
  startedAt: string | null;
  /* 节点结束时间，无则为 null */
  finishedAt: string | null;
  /* 按 kind 变化的键值详情 */
  detail: Record<string, unknown>;
}

export interface RunTrace {
  /* 轨迹所属 Run ID */
  runId: string;
  /* Run 所属 Skill */
  skill: string;
  /* 关联工作项 ID，未绑定需求时为 null */
  workItemId: number | null;
  /* Run 状态 */
  status: string;
  /* 是否已进入终态 */
  terminal: boolean;
  /* 开始时间，尚未开始为 null */
  startedAt: string | null;
  /* 结束时间，未结束为 null */
  finishedAt: string | null;
  /* 失败时的机器可读错误码 */
  errorCode: string | null;
  /* 已持久化的最大事件序号 */
  lastSequence: number;
  /* 按事件序号有序的轨迹节点 */
  nodes: TraceNode[];
}

export function createAgentRun(input: { workItemId: number; skill: "PRODUCT" | "UX" | "DEVELOPER" | "QA" | "RELEASE"; message: string }, client: RequestClient = apiClient): Promise<AgentRunSnapshot> {
  return client.request("/v1/agent-runs", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...input, mediumToolConfirmation: "ASK", clientRequestId: crypto.randomUUID() }),
  });
}

export function getAgentRun(organizationId: number, runId: string, client: RequestClient = apiClient): Promise<AgentRunSnapshot> {
  return client.request(`/v1/agent-runs/${runId}?organizationId=${organizationId}`);
}

export function cancelAgentRun(runId: string, client: RequestClient = apiClient): Promise<AgentRunSnapshot> {
  return client.request(`/v1/agent-runs/${runId}:cancel`, { method: "POST" });
}

/* 读取指定会话的 Run 摘要列表，按 Run 创建时间升序返回。 */
export function listConversationRuns(conversationId: number, client: RequestClient = apiClient): Promise<ConversationRun[]> {
  return client.request(`/v1/agent-conversations/${conversationId}/runs`);
}

/* 读取指定 Run 的轨迹详情，未结束的 Run 返回当前已持久化节点。 */
export function getRunTrace(runId: string, client: RequestClient = apiClient): Promise<RunTrace> {
  return client.request(`/v1/agent-runs/${runId}/trace`);
}

export function getRunApproval(organizationId: number, runId: string, client: RequestClient = apiClient): Promise<ApprovalSnapshot> {
  return client.request(`/v1/approvals/by-run/${runId}?organizationId=${organizationId}`);
}

export function listPendingApprovals(client: RequestClient = apiClient): Promise<PendingApproval[]> {
  return client.request("/v1/approvals?status=PENDING");
}

export function decideApproval(approval: { id: string; version: number }, organizationId: number, decision: "APPROVE" | "REJECT", client: RequestClient = apiClient): Promise<ApprovalSnapshot> {
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

export function cancelApproval(approval: ApprovalSnapshot, organizationId: number, client: RequestClient = apiClient): Promise<ApprovalSnapshot> {
  return client.request(`/v1/approvals/${approval.id}:cancel`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ organizationId, expectedVersion: approval.version }),
  });
}

export function agentEventUrl(organizationId: number, runId: string, afterSequence: number): string {
  const query = new URLSearchParams({
    organizationId: String(organizationId),
    afterSequence: String(afterSequence),
  });
  return `${resolveApiBaseUrl()}/v1/agent-runs/${runId}/events?${query}`;
}
