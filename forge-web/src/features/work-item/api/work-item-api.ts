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

export interface WorkItemDetail {
  id: number;
  itemKey: string;
  type: string;
  title: string;
  description: string;
  status: string;
  priority: string;
  assigneeUserId: number | null;
  reporterUserId: number;
  dueAt: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
  availableActions: string[];
  guardHints: Record<string, string[]>;
}

export interface DeliveryGraph {
  nodes: Array<{ id: string; kind: string; resourceId: number; type: string; title: string; status: string; depth: number }>;
  edges: Array<{ id: string; source: string; target: string; type: string }>;
  truncated: boolean;
  maxDepth: number;
  maxNodes: number;
}

export interface DevelopmentQaTask {
  id: number;
  itemKey: string;
  title: string;
  status: string;
  version: number;
  branchName: string | null;
  branchCommitSha: string | null;
  mergeRequestId: number | null;
  mergeRequestUrl: string | null;
  mergeRequestHeadSha: string | null;
  pipelineId: number | null;
  pipelineCommitSha: string | null;
  pipelineStatus: string | null;
  pipelineLastSyncedAt: string | null;
}

export interface DevelopmentQaSummary {
  requirementId: number;
  ciRequired: boolean;
  repositoryConfigured: boolean;
  tasks: DevelopmentQaTask[];
}

export const getRequirementWorkflow = (id: number, client: RequestClient = apiClient) => client.request<RequirementWorkflow>(`/v1/work-items/${id}`);

export const transitionRequirementWorkflow = (id: number, action: RequirementWorkflowAction, expectedVersion: number, options: { reason?: string; checklist?: string[] } = {}, client: RequestClient = apiClient) =>
  client.request(`/v1/work-items/${id}/transitions`, json("POST", { action, expectedVersion, idempotencyKey: crypto.randomUUID(), reason: options.reason ?? null, checklist: options.checklist ?? null }));

export const getWorkItem = (id: number, client: RequestClient = apiClient) => client.request<WorkItemDetail>(`/v1/work-items/${id}`);

export const getDeliveryGraph = (workItemId: number, client: RequestClient = apiClient) => client.request<DeliveryGraph>(`/v1/work-items/${workItemId}/delivery-graph`);

export const getDevelopmentSummary = (_organizationId: number, requirementId: number, client: RequestClient = apiClient) => client.request<DevelopmentQaSummary>(`/v1/development/requirements/${requirementId}`);

export const createDevTask = (input: { organizationId: number; requirementId: number; title: string; description?: string; assigneeUserId?: number }, client: RequestClient = apiClient) =>
  client.request(`/v1/development/requirements/${input.requirementId}/tasks`, json("POST", { title: input.title, description: input.description ?? "", assigneeUserId: input.assigneeUserId ?? null }));

export const startDevelopment = (input: { organizationId: number; taskId: number; targetBranch?: string; idempotencyKey: string }, client: RequestClient = apiClient) =>
  client.request(`/v1/development/tasks/${input.taskId}/start`, json("POST", { targetBranch: input.targetBranch ?? null, idempotencyKey: input.idempotencyKey }));

export const completeDevTask = (input: { organizationId: number; taskId: number; expectedVersion: number }, client: RequestClient = apiClient) => client.request(`/v1/development/tasks/${input.taskId}/complete`, json("POST", { expectedVersion: input.expectedVersion }));

export type TestCasePriority = "P0" | "P1" | "P2";
export type TestCaseType = "FUNCTIONAL" | "REGRESSION" | "E2E";
export type TestResultStatus = "NOT_RUN" | "PASS" | "FAIL" | "BLOCKED" | "SKIPPED";

export interface TestCaseView {
  id: number;
  caseKey: string;
  requirementId: number;
  title: string;
  preconditions: string;
  steps: string[];
  expectedResult: string;
  priority: TestCasePriority;
  caseType: TestCaseType;
  createdBy: number;
  createdByName: string;
  version: number;
}

export interface TestRunView {
  id: number;
  requirementId: number;
  environment: string;
  startedBy: number;
  startedByName: string;
  triggerSource: "USER" | "AGENT";
  status: "DRAFT" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
  summary: null | { total: number; passed: number; failed: number; blocked: number; skipped: number; notRun: number; mandatorySkipped: number };
  decision: { passed: boolean; missing: string[] };
  results: Array<{
    id: number;
    testCaseId: number;
    title: string;
    priority: TestCasePriority;
    status: TestResultStatus;
    actualResult: string;
    evidence: string[];
    executedBy?: number | null;
    executedByName?: string | null;
    executedAt?: string | null;
    version: number;
  }>;
  startedAt?: string;
  finishedAt?: string | null;
  version: number;
}

export interface BugView {
  id: number;
  itemKey: string;
  title: string;
  status: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "VERIFIED" | "CLOSED" | "REOPENED" | "CANCELLED";
  severity: "MINOR" | "MAJOR" | "CRITICAL" | "BLOCKER";
  requirementId: number;
  testRunId: number | null;
  testResultId: number | null;
  devTaskId: number | null;
  devTaskKey: string | null;
  assigneeUserId: number | null;
  assigneeName: string | null;
  reproductionSteps: string[];
  expectedResult: string;
  actualResult: string;
  fixNote: string | null;
  fixEvidence: string[];
  version: number;
}

export const listTestCases = (_organizationId: number, requirementId: number, client: RequestClient = apiClient) => client.request<TestCaseView[]>(`/v1/qa/requirements/${requirementId}/cases`);

export const createTestCase = (input: { organizationId: number; requirementId: number; title: string; preconditions: string; steps: string[]; expectedResult: string; priority: TestCasePriority; caseType: TestCaseType }, client: RequestClient = apiClient) =>
  client.request<TestCaseView>(`/v1/qa/requirements/${input.requirementId}/cases`, json("POST", { title: input.title, preconditions: input.preconditions, steps: input.steps, expectedResult: input.expectedResult, priority: input.priority, caseType: input.caseType }));

export const getLatestTestRun = (_organizationId: number, requirementId: number, client: RequestClient = apiClient) => client.request<TestRunView>(`/v1/qa/requirements/${requirementId}/runs/latest`);

export const createTestRun = (input: { organizationId: number; requirementId: number; environment: string }, client: RequestClient = apiClient) => client.request<TestRunView>(`/v1/qa/requirements/${input.requirementId}/runs`, json("POST", { environment: input.environment }));

export const updateTestResult = (input: { organizationId: number; runId: number; resultId: number; status: Exclude<TestResultStatus, "NOT_RUN">; actualResult?: string; evidence?: string[]; expectedVersion: number }, client: RequestClient = apiClient) =>
  client.request<TestRunView["results"][number]>(`/v1/qa/runs/${input.runId}/results/${input.resultId}`, json("PUT", { status: input.status, actualResult: input.actualResult ?? "", evidence: input.evidence ?? [], expectedVersion: input.expectedVersion }));

export const completeTestRun = (input: { organizationId: number; runId: number; expectedVersion: number }, client: RequestClient = apiClient) => client.request<TestRunView>(`/v1/qa/runs/${input.runId}/complete`, json("POST", { expectedVersion: input.expectedVersion }));

export const reopenTestRun = (input: { organizationId: number; runId: number; expectedVersion: number; reason: string }, client: RequestClient = apiClient) =>
  client.request<TestRunView>(`/v1/qa/runs/${input.runId}/reopen`, json("POST", { expectedVersion: input.expectedVersion, reason: input.reason }));

export const listBugs = (_organizationId: number, requirementId: number, client: RequestClient = apiClient) => client.request<BugView[]>(`/v1/bugs?${new URLSearchParams({ requirementId: String(requirementId) })}`);

export const createBug = (input: { organizationId: number; requirementId: number; testRunId?: number | null; testResultId?: number | null; title: string; severity: BugView["severity"]; reproductionSteps: string[]; expectedResult: string; actualResult: string }, client: RequestClient = apiClient) =>
  client.request<BugView>(
    "/v1/bugs",
    json("POST", {
      requirementId: input.requirementId,
      testRunId: input.testRunId ?? null,
      testResultId: input.testResultId ?? null,
      devTaskId: null,
      title: input.title,
      severity: input.severity,
      reproductionSteps: input.reproductionSteps,
      expectedResult: input.expectedResult,
      actualResult: input.actualResult,
    }),
  );

export const transitionBug = (input: { organizationId: number; bugId: number; action: "START_FIX" | "RESOLVE" | "VERIFY" | "CLOSE" | "REOPEN"; expectedVersion: number; reason?: string; fixEvidence?: string[]; idempotencyKey: string }, client: RequestClient = apiClient) =>
  client.request<BugView>(`/v1/bugs/${input.bugId}/transitions`, json("POST", { action: input.action, expectedVersion: input.expectedVersion, reason: input.reason ?? null, fixEvidence: input.fixEvidence ?? [], idempotencyKey: input.idempotencyKey }));

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
