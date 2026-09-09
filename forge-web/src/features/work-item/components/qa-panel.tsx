"use client";

import { Alert, Button, Card, Input, Select, Space, Tag, Typography } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { formatRequestError } from "@/lib/api";
import { bugActionLabel, guardHintLabel, testResultLabel } from "@/lib/labels";

import {
  completeTestRun,
  createBug,
  createTestCase,
  createTestRun,
  getLatestTestRun,
  listBugs,
  listTestCases,
  reopenTestRun,
  updateTestResult,
  transitionBug,
  type BugView,
  type TestRunView,
} from "../api/work-item-api";

export function QaPanel({ workspaceId, projectId, requirementId, onChanged }: {
  workspaceId: number;
  projectId: number;
  requirementId: number;
  onChanged: () => Promise<unknown>;
}) {
  const client = useQueryClient();
  const key = ["qa", workspaceId, projectId, requirementId];
  const cases = useQuery({ queryKey: [...key, "cases"], queryFn: () => listTestCases(workspaceId, projectId, requirementId) });
  const run = useQuery({ queryKey: [...key, "run"], queryFn: () => getLatestTestRun(workspaceId, projectId, requirementId), retry: false });
  const bugs = useQuery({ queryKey: [...key, "bugs"], queryFn: () => listBugs(workspaceId, projectId, requirementId) });
  const [title, setTitle] = useState("");
  const refresh = async () => {
    await Promise.all([client.invalidateQueries({ queryKey: key }), onChanged()]);
  };
  const createCase = useMutation({
    mutationFn: () => createTestCase({ workspaceId, projectId, requirementId, title, preconditions: "", steps: ["执行测试"], expectedResult: "符合预期", priority: "P1" }),
    onSuccess: () => { setTitle(""); void refresh(); },
  });
  const createRun = useMutation({
    mutationFn: () => createTestRun({ workspaceId, projectId, requirementId, environment: "staging" }),
    onSuccess: refresh,
  });
  const update = useMutation({
    mutationFn: ({ resultId, status, version }: { resultId: number; status: "PASS" | "FAIL" | "BLOCKED" | "SKIPPED"; version: number }) =>
      updateTestResult({ workspaceId, projectId, runId: run.data!.id, resultId, status, expectedVersion: version }),
    onSuccess: refresh,
  });
  const complete = useMutation({
    mutationFn: () => completeTestRun({ workspaceId, projectId, runId: run.data!.id, expectedVersion: run.data!.version }),
    onSuccess: refresh,
  });
  const reopen = useMutation({
    mutationFn: () => reopenTestRun({ workspaceId, projectId, runId: run.data!.id, expectedVersion: run.data!.version, reason: "重新回归" }),
    onSuccess: refresh,
  });
  const createFailureBug = useMutation({
    mutationFn: (result: TestRunView["results"][number]) => createBug({
      workspaceId,
      projectId,
      requirementId,
      testRunId: run.data!.id,
      testResultId: result.id,
      title: `${result.title} 失败`,
      severity: "MAJOR",
      reproductionSteps: [result.title],
      expectedResult: "符合 Test Case 预期",
      actualResult: result.actualResult || result.status,
    }),
    onSuccess: refresh,
  });
  const moveBug = useMutation({
    mutationFn: ({ bug, action, reason, evidence }: {
      bug: BugView;
      action: "START_FIX" | "RESOLVE" | "VERIFY" | "CLOSE" | "REOPEN";
      reason?: string;
      evidence?: string;
    }) => transitionBug({
      workspaceId,
      projectId,
      bugId: bug.id,
      action,
      expectedVersion: bug.version,
      reason: reason ?? (action === "REOPEN" ? "重新回归失败" : ""),
      fixEvidence: evidence ? [evidence] : [],
      idempotencyKey: crypto.randomUUID(),
    }),
    onSuccess: refresh,
  });
  const error = createCase.error ?? createRun.error ?? update.error ?? complete.error ?? reopen.error ?? createFailureBug.error ?? moveBug.error;
  return (
    <Card title="QA 验证">
      <Space direction="vertical" style={{ width: "100%" }}>
        {error && <Alert type="error" content={formatRequestError(error)} />}
        <Space>
          <Input aria-label="测试用例标题" value={title} onChange={setTitle} />
          <Button disabled={!title.trim()} onClick={() => createCase.mutate()}>创建用例</Button>
          <Button disabled={!cases.data?.length} onClick={() => createRun.mutate()}>创建测试执行</Button>
        </Space>
        <Typography.Text>有效用例：{cases.data?.length ?? 0}</Typography.Text>
        {run.data && <QaRunView run={run.data} busy={update.isPending} onResult={(resultId, status, version) => update.mutate({ resultId, status, version })} onCreateBug={(result) => createFailureBug.mutate(result)} onComplete={() => complete.mutate()} onReopen={() => reopen.mutate()} />}
        <BugList bugs={bugs.data ?? []} onAction={(bug, action, reason, evidence) => moveBug.mutate({ bug, action, reason, evidence })} />
      </Space>
    </Card>
  );
}

export function QaRunView({ run, busy, onResult, onCreateBug, onComplete, onReopen }: {
  run: TestRunView;
  busy: boolean;
  onResult: (resultId: number, status: "PASS" | "FAIL" | "BLOCKED" | "SKIPPED", version: number) => void;
  onCreateBug?: (result: TestRunView["results"][number]) => void;
  onComplete: () => void;
  onReopen: () => void;
}) {
  return (
    <Space direction="vertical" style={{ width: "100%" }}>
      <Space><Tag>{run.status}</Tag><Typography.Text>{run.environment}</Typography.Text></Space>
      {run.results.map((result) => (
        <Card key={result.id} size="small" title={`${result.priority} · ${result.title}`}>
          <Select aria-label={`${result.title} 结果`} value={result.status} disabled={busy || run.status === "COMPLETED"} onChange={(status) => status !== "NOT_RUN" && onResult(result.id, status, result.version)}>
            {["NOT_RUN", "PASS", "FAIL", "BLOCKED", "SKIPPED"].map((status) => <Select.Option key={status} value={status}>{testResultLabel(status)}</Select.Option>)}
          </Select>
          {result.status === "FAIL" && onCreateBug && <Button onClick={() => onCreateBug(result)}>创建关联 Bug</Button>}
        </Card>
      ))}
      {run.summary && <Alert type={run.decision.passed ? "success" : "warning"} content={`通过 ${run.summary.passed}/${run.summary.total}；${run.decision.passed ? "允许 QA 通过" : run.decision.missing.map((value) => guardHintLabel(value)).join("、")}`} />}
      {run.status === "COMPLETED"
        ? <Button onClick={onReopen}>重新打开测试执行</Button>
        : <Button disabled={run.results.some((result) => result.status === "NOT_RUN")} onClick={onComplete}>完成测试执行</Button>}
    </Space>
  );
}

export function BugList({ bugs, onAction }: {
  bugs: BugView[];
  onAction: (
    bug: BugView,
    action: "START_FIX" | "RESOLVE" | "VERIFY" | "CLOSE" | "REOPEN",
    reason?: string,
    evidence?: string,
  ) => void;
}) {
  const [fixNotes, setFixNotes] = useState<Record<number, string>>({});
  const [fixEvidence, setFixEvidence] = useState<Record<number, string>>({});
  const nextAction = (status: BugView["status"]) => ({
    OPEN: "START_FIX",
    IN_PROGRESS: "RESOLVE",
    RESOLVED: "VERIFY",
    VERIFIED: "CLOSE",
    CLOSED: "REOPEN",
  } as const)[status as "OPEN" | "IN_PROGRESS" | "RESOLVED" | "VERIFIED" | "CLOSED"];
  return (
    <Space direction="vertical" style={{ width: "100%" }}>
      <Typography.Title heading={6}>关联 Bug</Typography.Title>
      {bugs.map((bug) => {
        const action = nextAction(bug.status);
        return (
          <Card key={bug.id} size="small" title={`${bug.itemKey} · ${bug.title}`}>
            <Space>
              <Tag color={bug.severity === "BLOCKER" || bug.severity === "CRITICAL" ? "red" : "orange"}>{bug.severity}</Tag>
              <Tag>{bug.status}</Tag>
              {bug.status === "IN_PROGRESS" && (
                <>
                  <Input
                    aria-label={`${bug.itemKey} 修复说明`}
                    placeholder="修复说明"
                    value={fixNotes[bug.id] ?? ""}
                    onChange={(value) => setFixNotes((current) => ({ ...current, [bug.id]: value }))}
                  />
                  <Input
                    aria-label={`${bug.itemKey} 修复证据`}
                    placeholder="MR 或 Commit 引用"
                    value={fixEvidence[bug.id] ?? ""}
                    onChange={(value) => setFixEvidence((current) => ({ ...current, [bug.id]: value }))}
                  />
                </>
              )}
              {action && (
                <Button
                  disabled={action === "RESOLVE" && (!fixNotes[bug.id]?.trim() || !fixEvidence[bug.id]?.trim())}
                  onClick={() => onAction(bug, action, fixNotes[bug.id], fixEvidence[bug.id])}
                >
                  {bugActionLabel(action)}
                </Button>
              )}
            </Space>
          </Card>
        );
      })}
    </Space>
  );
}
