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
  | "SKIP_UX"
  | "SUBMIT_FOR_QA"
  | "START_QA"
  | "QA_PASS"
  | "START"
  | "SUBMIT_REVIEW"
  | "APPROVE"
  | "REJECT";
export interface WorkItem {
  id: number;
  workspaceId: number;
  projectId: number;
  type?: "REQUIREMENT" | "UX_TASK" | "DEV_TASK" | "QA_TASK" | "BUG";
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
export interface ActivityItem {
  kind: "EVENT" | "COMMENT";
  id: number;
  action: WorkflowAction | null;
  actorId: number;
  reason: string | null;
  body: string | null;
  createdAt: string;
}
export interface DeliveryGraph {
  nodes: DeliveryGraphNode[];
  edges: DeliveryGraphEdge[];
  truncated: boolean;
  maxDepth: number;
  maxNodes: number;
}
export interface DeliveryGraphNode {
  id: string;
  kind: "WORK_ITEM" | "DOCUMENT" | "SOURCE_CONTROL" | "QA" | "RELEASE";
  resourceId: number;
  type: string;
  title: string;
  status: string;
  depth: number;
}
export interface DeliveryGraphEdge {
  id: string;
  source: string;
  target: string;
  type: string;
}
export interface DevelopmentResult {
  branch: { id: number; name: string; commitSha: string };
  mergeRequest: { id: number; remoteMrIid: number; webUrl: string; state: string };
  reconciled: boolean;
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
export type TestResultStatus = "NOT_RUN" | "PASS" | "FAIL" | "BLOCKED" | "SKIPPED";
export interface TestCaseView {
  id: number;
  requirementId: number;
  title: string;
  preconditions: string;
  steps: string[];
  expectedResult: string;
  priority: "P0" | "P1" | "P2";
  version: number;
}
export interface TestResultView {
  id: number;
  testCaseId: number;
  title: string;
  priority: "P0" | "P1" | "P2";
  status: TestResultStatus;
  actualResult: string;
  evidence: string[];
  version: number;
}
export interface TestRunView {
  id: number;
  requirementId: number;
  environment: string;
  status: "DRAFT" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
  summary: null | {
    total: number;
    passed: number;
    failed: number;
    blocked: number;
    skipped: number;
    notRun: number;
    mandatorySkipped: number;
  };
  decision: { passed: boolean; missing: string[] };
  results: TestResultView[];
  version: number;
}
export interface BugView {
  id: number;
  itemKey: string;
  title: string;
  status: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "VERIFIED" | "CLOSED" | "CANCELLED";
  severity: "BLOCKER" | "CRITICAL" | "MAJOR" | "MINOR";
  requirementId: number;
  testRunId: number | null;
  testResultId: number | null;
  reproductionSteps: string[];
  expectedResult: string;
  actualResult: string;
  fixNote: string | null;
  fixEvidence: string[];
  version: number;
}
export type BugAction = "START_FIX" | "RESOLVE" | "VERIFY" | "CLOSE" | "REOPEN" | "CANCEL";
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
export const getWorkItemActivity = (
  workspaceId: number,
  projectId: number,
  id: number,
  client: RequestClient = apiClient,
) =>
  client.request<ActivityItem[]>(
    `/v1/work-items/${id}/activity?workspaceId=${workspaceId}&projectId=${projectId}`,
  );
export const getDeliveryGraph = (
  workspaceId: number,
  projectId: number,
  id: number,
  client: RequestClient = apiClient,
) =>
  client.request<DeliveryGraph>(
    `/v1/work-items/${id}/delivery-graph?workspaceId=${workspaceId}&projectId=${projectId}`,
  );
export const createDevTask = (
  input: {
    workspaceId: number;
    projectId: number;
    requirementId: number;
    title: string;
    description: string;
  },
  client: RequestClient = apiClient,
) =>
  client.request<WorkItem>(
    `/v1/development/requirements/${input.requirementId}/tasks`,
    json("POST", input),
  );
export const startDevelopment = (
  input: {
    workspaceId: number;
    projectId: number;
    taskId: number;
    targetBranch?: string;
    idempotencyKey: string;
  },
  client: RequestClient = apiClient,
) =>
  client.request<DevelopmentResult>(
    `/v1/development/tasks/${input.taskId}/start`,
    json("POST", input),
  );
export const getDevelopmentSummary = (
  workspaceId: number,
  projectId: number,
  requirementId: number,
  client: RequestClient = apiClient,
) =>
  client.request<DevelopmentQaSummary>(
    `/v1/development/requirements/${requirementId}?workspaceId=${workspaceId}&projectId=${projectId}`,
  );
export const completeDevTask = (
  input: {
    workspaceId: number;
    projectId: number;
    taskId: number;
    expectedVersion: number;
  },
  client: RequestClient = apiClient,
) =>
  client.request<WorkItem>(
    `/v1/development/tasks/${input.taskId}/complete`,
    json("POST", input),
  );
export const listTestCases = (
  workspaceId: number,
  projectId: number,
  requirementId: number,
  client: RequestClient = apiClient,
) => client.request<TestCaseView[]>(
  `/v1/qa/requirements/${requirementId}/cases?workspaceId=${workspaceId}&projectId=${projectId}`,
);
export const createTestCase = (
  input: {
    workspaceId: number;
    projectId: number;
    requirementId: number;
    title: string;
    preconditions: string;
    steps: string[];
    expectedResult: string;
    priority: "P0" | "P1" | "P2";
  },
  client: RequestClient = apiClient,
) => client.request<TestCaseView>(
  `/v1/qa/requirements/${input.requirementId}/cases`,
  json("POST", input),
);
export const getLatestTestRun = (
  workspaceId: number,
  projectId: number,
  requirementId: number,
  client: RequestClient = apiClient,
) => client.request<TestRunView>(
  `/v1/qa/requirements/${requirementId}/runs/latest?workspaceId=${workspaceId}&projectId=${projectId}`,
);
export const createTestRun = (
  input: { workspaceId: number; projectId: number; requirementId: number; environment: string },
  client: RequestClient = apiClient,
) => client.request<TestRunView>(
  `/v1/qa/requirements/${input.requirementId}/runs`,
  json("POST", input),
);
export const updateTestResult = (
  input: {
    workspaceId: number;
    projectId: number;
    runId: number;
    resultId: number;
    status: Exclude<TestResultStatus, "NOT_RUN">;
    expectedVersion: number;
  },
  client: RequestClient = apiClient,
) => client.request<TestResultView>(
  `/v1/qa/runs/${input.runId}/results/${input.resultId}`,
  json("PUT", { ...input, actualResult: "", evidence: [] }),
);
export const completeTestRun = (
  input: { workspaceId: number; projectId: number; runId: number; expectedVersion: number },
  client: RequestClient = apiClient,
) => client.request<TestRunView>(
  `/v1/qa/runs/${input.runId}/complete`,
  json("POST", input),
);
export const reopenTestRun = (
  input: { workspaceId: number; projectId: number; runId: number; expectedVersion: number; reason: string },
  client: RequestClient = apiClient,
) => client.request<TestRunView>(
  `/v1/qa/runs/${input.runId}/reopen`,
  json("POST", input),
);
export const listBugs = (
  workspaceId: number,
  projectId: number,
  requirementId: number,
  client: RequestClient = apiClient,
) => client.request<BugView[]>(
  `/v1/bugs?workspaceId=${workspaceId}&projectId=${projectId}&requirementId=${requirementId}`,
);
export const createBug = (
  input: {
    workspaceId: number;
    projectId: number;
    requirementId: number;
    testRunId?: number;
    testResultId?: number;
    title: string;
    severity: BugView["severity"];
    reproductionSteps: string[];
    expectedResult: string;
    actualResult: string;
  },
  client: RequestClient = apiClient,
) => client.request<BugView>("/v1/bugs", json("POST", input));
export const transitionBug = (
  input: {
    workspaceId: number;
    projectId: number;
    bugId: number;
    action: BugAction;
    expectedVersion: number;
    reason?: string;
    fixEvidence?: string[];
    idempotencyKey: string;
  },
  client: RequestClient = apiClient,
) => client.request<BugView>(
  `/v1/bugs/${input.bugId}/transitions`,
  json("POST", input),
);
