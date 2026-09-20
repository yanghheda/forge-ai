"use client";

import { Alert, Button, Drawer, Form, Input, Select, Spin } from "@arco-design/web-react";
import { IconCheck, IconClose, IconExclamationCircle, IconPlus } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { canEditModule, getCurrentUser } from "@/features/auth";
import { formatRequestError } from "@/lib/api";
import { bugActionLabel, guardHintLabel, testResultLabel } from "@/lib/labels";
import { completeTestRun, createBug, createTestCase, createTestRun, getLatestTestRun, listBugs, listTestCases, transitionBug, updateTestResult, type BugView, type TestCasePriority, type TestCaseType, type TestRunView } from "../api/work-item-api";
import styles from "./qa-panel.module.css";

type ResultChoice = "IN_PROGRESS" | "PASS" | "FAIL" | "BLOCKED" | "SKIPPED";
type QaTab = "cases" | "bugs";

export function QaPanel({ organizationId, requirementId, onChanged }: { organizationId: number; requirementId: number; onChanged: () => Promise<unknown> }) {
  const client = useQueryClient();
  const key = ["qa", organizationId, requirementId];
  const user = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
  const cases = useQuery({ queryKey: [...key, "cases"], queryFn: () => listTestCases(organizationId, requirementId) });
  const run = useQuery({ queryKey: [...key, "run"], queryFn: () => getLatestTestRun(organizationId, requirementId), retry: false });
  const bugs = useQuery({ queryKey: [...key, "bugs"], queryFn: () => listBugs(organizationId, requirementId) });
  const [caseDrawer, setCaseDrawer] = useState(false);
  const [bugDrawer, setBugDrawer] = useState(false);
  const [activeTab, setActiveTab] = useState<QaTab>("cases");
  const [caseForm] = Form.useForm();
  const [bugForm] = Form.useForm();
  const refresh = async () => Promise.all([client.invalidateQueries({ queryKey: key }), onChanged()]);
  const createCase = useMutation({
    mutationFn: (value: { title: string; priority: TestCasePriority; caseType: TestCaseType; preconditions?: string; steps: string; expectedResult: string }) =>
      createTestCase({
        organizationId,
        requirementId,
        title: value.title,
        priority: value.priority,
        caseType: value.caseType,
        preconditions: value.preconditions ?? "",
        steps: value.steps
          .split("\n")
          .map((item) => item.trim())
          .filter(Boolean),
        expectedResult: value.expectedResult,
      }),
    onSuccess: () => {
      setCaseDrawer(false);
      caseForm.resetFields();
      void refresh();
    },
  });
  const createRun = useMutation({ mutationFn: () => createTestRun({ organizationId, requirementId, environment: "staging" }), onSuccess: refresh });
  const update = useMutation({
    mutationFn: async ({ testCaseId, status }: { testCaseId: number; status: ResultChoice }) => {
      const currentRun = run.data;
      const currentResult = currentRun?.results.find((item) => item.testCaseId === testCaseId);
      const writableRun = currentRun && !["COMPLETED", "CANCELLED"].includes(currentRun.status) && currentResult ? currentRun : await createTestRun({ organizationId, requirementId, environment: "staging" });
      const writableResult = writableRun.results.find((item) => item.testCaseId === testCaseId);
      if (!writableResult) throw new Error("新测试轮次中缺少对应测试用例");
      return updateTestResult({ organizationId, runId: writableRun.id, resultId: writableResult.id, status, expectedVersion: writableResult.version });
    },
    onSuccess: refresh,
  });
  const complete = useMutation({ mutationFn: () => completeTestRun({ organizationId, runId: run.data!.id, expectedVersion: run.data!.version }), onSuccess: refresh });
  const createManualBug = useMutation({
    mutationFn: (value: { title: string; severity: BugView["severity"]; reproductionSteps: string; expectedResult: string; actualResult: string }) =>
      createBug({
        organizationId,
        requirementId,
        title: value.title,
        severity: value.severity,
        reproductionSteps: value.reproductionSteps
          .split("\n")
          .map((item) => item.trim())
          .filter(Boolean),
        expectedResult: value.expectedResult,
        actualResult: value.actualResult,
      }),
    onSuccess: () => {
      setBugDrawer(false);
      bugForm.resetFields();
      void refresh();
    },
  });
  const moveBug = useMutation({
    mutationFn: ({ bug, action, reason, evidence }: { bug: BugView; action: BugAction; reason?: string; evidence?: string }) =>
      transitionBug({ organizationId, bugId: bug.id, action, expectedVersion: bug.version, reason: reason ?? (action === "REOPEN" ? "重新回归失败" : ""), fixEvidence: evidence ? [evidence] : [], idempotencyKey: crypto.randomUUID() }),
    onSuccess: refresh,
  });
  const error = cases.error ?? bugs.error ?? createCase.error ?? createRun.error ?? update.error ?? complete.error ?? createManualBug.error ?? moveBug.error;
  const stats = runStats(run.data);
  const blockingBugs = (bugs.data ?? []).filter((bug) => ["BLOCKER", "CRITICAL"].includes(bug.severity) && !["VERIFIED", "CLOSED", "CANCELLED"].includes(bug.status));

  if (cases.isPending || bugs.isPending)
    return (
      <div className={styles.loading}>
        <Spin tip="正在加载 QA 数据…" />
      </div>
    );
  const readOnly = !canEditModule(user.data?.organization.roles, "qa");
  return (
    <div className={styles.workspace}>
      {error && <Alert type="error" content={formatRequestError(error)} />}
      <div className={styles.actions}>
        {activeTab === "cases" ? (
          <>
            <Button disabled>导入用例</Button>
            <Button type="primary" icon={<IconPlus />} disabled={readOnly} onClick={() => setCaseDrawer(true)}>
              新建测试用例
            </Button>
          </>
        ) : (
          <Button type="primary" icon={<IconPlus />} disabled={readOnly} onClick={() => setBugDrawer(true)}>
            创建缺陷
          </Button>
        )}
      </div>
      <section className={styles.stats} aria-label="测试统计">
        <Metric label="用例总数" value={cases.data?.length ?? 0} note={`P0 ${(cases.data ?? []).filter((item) => item.priority === "P0").length} · P1 ${(cases.data ?? []).filter((item) => item.priority === "P1").length} · P2 ${(cases.data ?? []).filter((item) => item.priority === "P2").length}`} />
        <Metric label="通过" value={stats.passed} tone="green" note={run.data ? `RUN-${run.data.id} 最新执行` : "暂无测试执行"} />
        <Metric label="失败" value={stats.failed} tone="red" note="来自最新测试执行" />
        <Metric label="阻塞" value={stats.blocked} tone="orange" note="来自最新测试执行" />
        <Metric label="通过率" value={`${stats.rate}%`} tone={run.data?.decision.passed ? "green" : "red"} note="以服务端门禁结论为准" progress={stats.rate} />
      </section>
      <div className={styles.tabs} role="tablist" aria-label="QA 数据视图">
        <button type="button" role="tab" aria-selected={activeTab === "cases"} onClick={() => setActiveTab("cases")}>
          测试用例
        </button>
        <button type="button" role="tab" aria-selected={activeTab === "bugs"} onClick={() => setActiveTab("bugs")}>
          缺陷 {(bugs.data ?? []).length}
        </button>
      </div>
      {activeTab === "cases" && (
        <div className={styles.mainGrid} role="tabpanel">
          <CaseTable cases={cases.data ?? []} run={run.data} readOnly={readOnly} busy={update.isPending || createRun.isPending || complete.isPending} onStart={() => createRun.mutate()} onComplete={() => complete.mutate()} onResult={(testCaseId, status) => update.mutate({ testCaseId, status })} />
          <GatePanel run={run.data} blockingBugs={blockingBugs} />
        </div>
      )}
      {activeTab === "bugs" && (
        <div role="tabpanel">
          <BugList bugs={bugs.data ?? []} readOnly={readOnly} onAction={(bug, action, reason, evidence) => moveBug.mutate({ bug, action, reason, evidence })} />
        </div>
      )}
      <Drawer
        width={560}
        title="新建测试用例"
        visible={caseDrawer}
        onCancel={() => setCaseDrawer(false)}
        footer={
          <>
            <Button onClick={() => setCaseDrawer(false)}>取消</Button>
            <Button type="primary" loading={createCase.isPending} onClick={() => caseForm.submit()}>
              创建测试用例
            </Button>
          </>
        }
      >
        <Form form={caseForm} layout="vertical" initialValues={{ priority: "P1", caseType: "FUNCTIONAL" }} onSubmit={(value) => createCase.mutate(value)}>
          <Form.Item field="title" label="标题" required rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item field="priority" label="优先级" required>
            <Select options={["P0", "P1", "P2"].map((value) => ({ label: value, value }))} />
          </Form.Item>
          <Form.Item field="caseType" label="类型" required>
            <Select
              options={[
                { label: "功能", value: "FUNCTIONAL" },
                { label: "回归", value: "REGRESSION" },
                { label: "E2E", value: "E2E" },
              ]}
            />
          </Form.Item>
          <Form.Item field="preconditions" label="前置条件">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item field="steps" label="测试步骤（每行一步）" required rules={[{ required: true }]}>
            <Input.TextArea rows={5} />
          </Form.Item>
          <Form.Item field="expectedResult" label="预期结果" required rules={[{ required: true }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Drawer>
      <Drawer
        width={560}
        title="创建缺陷"
        visible={bugDrawer}
        onCancel={() => setBugDrawer(false)}
        footer={
          <>
            <Button onClick={() => setBugDrawer(false)}>取消</Button>
            <Button type="primary" loading={createManualBug.isPending} onClick={() => bugForm.submit()}>
              创建缺陷
            </Button>
          </>
        }
      >
        <div role="dialog" aria-label="创建缺陷">
          <Form form={bugForm} layout="vertical" initialValues={{ severity: "MAJOR" }} onSubmit={(value) => createManualBug.mutate(value)}>
            <Form.Item field="title" label="缺陷标题" required rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item field="severity" label="严重级别" required>
              <Select options={["MINOR", "MAJOR", "CRITICAL", "BLOCKER"].map((value) => ({ label: value, value }))} />
            </Form.Item>
            <Form.Item field="reproductionSteps" label="复现步骤（每行一步）" required rules={[{ required: true }]}>
              <Input.TextArea rows={5} />
            </Form.Item>
            <Form.Item field="expectedResult" label="预期结果" required rules={[{ required: true }]}>
              <Input.TextArea rows={3} />
            </Form.Item>
            <Form.Item field="actualResult" label="实际结果" required rules={[{ required: true }]}>
              <Input.TextArea rows={3} />
            </Form.Item>
          </Form>
        </div>
      </Drawer>
    </div>
  );
}

function runStats(run?: TestRunView) {
  const passed = run?.summary?.passed ?? run?.results.filter((item) => item.status === "PASS").length ?? 0;
  const failed = run?.summary?.failed ?? run?.results.filter((item) => item.status === "FAIL").length ?? 0;
  const blocked = run?.summary?.blocked ?? run?.results.filter((item) => item.status === "BLOCKED").length ?? 0;
  const total = run?.summary?.total ?? run?.results.length ?? 0;
  return { passed, failed, blocked, rate: total ? Math.round((passed / total) * 100) : 0 };
}

function Metric({ label, value, note, tone = "blue", progress }: { label: string; value: string | number; note: string; tone?: "blue" | "green" | "red" | "orange"; progress?: number }) {
  return (
    <article className={styles.metric}>
      <span>{label}</span>
      <strong className={styles[tone]}>{value}</strong>
      <small>{note}</small>
      {progress !== undefined && (
        <div className={styles.progress}>
          <i style={{ width: `${progress}%` }} />
        </div>
      )}
    </article>
  );
}

function CaseTable({
  cases,
  run,
  readOnly = false,
  busy,
  onStart,
  onComplete,
  onResult,
}: {
  cases: Awaited<ReturnType<typeof listTestCases>>;
  run?: TestRunView;
  readOnly?: boolean;
  busy: boolean;
  onStart: () => void;
  onComplete: () => void;
  onResult: (testCaseId: number, status: ResultChoice) => void;
}) {
  const results = new Map(run?.results.map((item) => [item.testCaseId, item]));
  const [selected, setSelected] = useState<(typeof cases)[number]>();
  const selectedResult = selected ? results.get(selected.id) : undefined;
  const activeRun = run && run.status !== "COMPLETED" && run.status !== "CANCELLED";
  const canComplete = Boolean(activeRun && run.results.length && run.results.every((item) => !["NOT_RUN", "IN_PROGRESS"].includes(item.status)));
  return (
    <section className={styles.panel}>
      <div className={styles.toolbar}>
        <span>全部优先级</span>
        <span>全部类型</span>
        <span className={styles.search}>搜索用例编号 / 标题</span>
        {!readOnly &&
          (activeRun ? (
            <Button size="small" type="primary" loading={busy} disabled={!canComplete} onClick={onComplete}>
              完成本轮测试
            </Button>
          ) : (
            <Button size="small" type="primary" loading={busy} disabled={!cases.length} onClick={onStart}>
              {run?.status === "COMPLETED" ? "开始新一轮测试" : "开始人工测试"}
            </Button>
          ))}
      </div>
      <div className={styles.tableWrap}>
        <table>
          <thead>
            <tr>
              <th>用例编号</th>
              <th>标题</th>
              <th>优先级</th>
              <th>类型</th>
              <th>最近结果</th>
              <th>执行人</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {cases.map((item) => {
              const result = results.get(item.id);
              return (
                <tr key={item.id}>
                  <td>
                    <code>{item.caseKey}</code>
                  </td>
                  <td>
                    <button type="button" className={styles.linkButton} aria-label={`查看用例 ${item.caseKey}`} onClick={() => setSelected(item)}>
                      {item.title}
                    </button>
                  </td>
                  <td>
                    <Status value={item.priority} />
                  </td>
                  <td>{item.caseType === "FUNCTIONAL" ? "功能" : item.caseType === "REGRESSION" ? "回归" : "E2E"}</td>
                  <td>
                    <ResultStatus value={result?.status ?? "NOT_RUN"} />
                  </td>
                  <td>{result?.executedByName ?? (result?.executedBy ? `#${result.executedBy}` : "—")}</td>
                  <td>
                    <Select
                      aria-label={`${item.title} 结果`}
                      size="small"
                      value={result?.status ?? "NOT_RUN"}
                      disabled={busy || readOnly}
                      onChange={(status) => status !== "NOT_RUN" && onResult(item.id, status)}
                      options={[{ label: testResultLabel("NOT_RUN"), value: "NOT_RUN", disabled: true }, ...["IN_PROGRESS", "PASS", "FAIL", "BLOCKED", "SKIPPED"].map((value) => ({ label: testResultLabel(value), value }))]}
                    />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <footer>展示 {cases.length} 条用例</footer>
      <Drawer width={520} title="测试用例详情" visible={Boolean(selected)} onCancel={() => setSelected(undefined)} footer={null}>
        {selected && (
          <div className={styles.detail} role="dialog" aria-label="测试用例详情">
            <header>
              <div>
                <code>{selected.caseKey}</code>
                <h3>{selected.title}</h3>
              </div>
              <ResultStatus value={selectedResult?.status ?? "NOT_RUN"} />
            </header>
            <DetailItem label="优先级 / 类型" value={`${selected.priority} / ${selected.caseType === "FUNCTIONAL" ? "功能" : selected.caseType === "REGRESSION" ? "回归" : "E2E"}`} />
            <DetailItem label="创建人" value={selected.createdByName} />
            <DetailItem label="前置条件" value={selected.preconditions || "无"} />
            <div>
              <b>测试步骤</b>
              <ol>
                {selected.steps.map((step, index) => (
                  <li key={`${index}-${step}`}>{step}</li>
                ))}
              </ol>
            </div>
            <DetailItem label="预期结果" value={selected.expectedResult} />
            {selectedResult?.actualResult && <DetailItem label="实际结果" value={selectedResult.actualResult} />}
            <DetailItem label="执行人" value={selectedResult?.executedByName ?? "尚未执行"} />
          </div>
        )}
      </Drawer>
    </section>
  );
}

function GatePanel({ run, blockingBugs }: { run?: TestRunView; blockingBugs: BugView[] }) {
  const stats = runStats(run);
  const missing = run?.decision.missing ?? [];
  const rows = [
    { label: "服务端 QA 放行判定", passed: Boolean(run?.decision.passed), detail: run ? (run.decision.passed ? "通过" : "未通过") : "暂无运行" },
    { label: "用例全部执行", passed: Boolean(run?.results.length) && !run?.results.some((item) => ["NOT_RUN", "IN_PROGRESS"].includes(item.status)), detail: run?.results.some((item) => ["NOT_RUN", "IN_PROGRESS"].includes(item.status)) ? "存在未完成" : "已满足" },
    { label: "无失败或阻塞结果", passed: stats.failed + stats.blocked === 0, detail: `${stats.failed} 失败 / ${stats.blocked} 阻塞` },
    { label: "阻断缺陷已清零", passed: blockingBugs.length === 0, detail: blockingBugs.length ? blockingBugs.map((bug) => bug.itemKey).join("、") : "0" },
  ];
  return (
    <aside className={styles.gate}>
      <header>
        <strong>QA 门禁（进入待发布条件）</strong>
        <Status value={run?.decision.passed ? "已通过" : "未通过"} />
      </header>
      {rows.map((row) => (
        <div className={styles.gateRow} key={row.label}>
          {row.passed ? <IconCheck className={styles.green} /> : <IconClose className={styles.red} />}
          <span>{row.label}</span>
          <b>{row.detail}</b>
        </div>
      ))}
      {run && !run.decision.passed && <Alert type="error" content={`QA 门禁未通过：${missing.map(guardHintLabel).join("、") || "请处理失败结果或阻断缺陷"}`} />}
    </aside>
  );
}

type BugAction = "START_FIX" | "RESOLVE" | "VERIFY" | "CLOSE" | "REOPEN";
export function BugList({ bugs, readOnly = false, onAction }: { bugs: BugView[]; readOnly?: boolean; onAction: (bug: BugView, action: BugAction, reason?: string, evidence?: string) => void }) {
  const [notes, setNotes] = useState<Record<number, string>>({});
  const [evidence, setEvidence] = useState<Record<number, string>>({});
  const [selected, setSelected] = useState<BugView>();
  const actions = { OPEN: "START_FIX", IN_PROGRESS: "RESOLVE", RESOLVED: "VERIFY", VERIFIED: "CLOSE", CLOSED: "REOPEN", REOPENED: "START_FIX" } as const;
  return (
    <section className={styles.panel}>
      <header className={styles.sectionHead}>
        <strong>
          <IconExclamationCircle /> 缺陷
        </strong>
        <span>OPEN → IN_PROGRESS → RESOLVED → VERIFIED → CLOSED</span>
      </header>
      <div className={styles.tableWrap}>
        <table>
          <thead>
            <tr>
              <th>缺陷</th>
              <th>状态</th>
              <th>严重级</th>
              <th>关联开发任务</th>
              <th>指派人</th>
              <th>修复信息</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {bugs.map((bug) => {
              const action = actions[bug.status as keyof typeof actions];
              return (
                <tr key={bug.id}>
                  <td>
                    <code>{bug.itemKey}</code>
                    <button type="button" className={`${styles.linkButton} ${styles.bugTitle}`} aria-label={`查看缺陷 ${bug.itemKey}`} onClick={() => setSelected(bug)}>
                      {bug.title}
                    </button>
                  </td>
                  <td>
                    <Status value={bug.status} />
                  </td>
                  <td>
                    <Status value={bug.severity} />
                  </td>
                  <td>{bug.devTaskKey ?? "—"}</td>
                  <td>{bug.assigneeName ?? "—"}</td>
                  <td>
                    {bug.status === "IN_PROGRESS" && !readOnly ? (
                      <div className={styles.fixInputs}>
                        <Input aria-label={`${bug.itemKey} 修复说明`} placeholder="修复说明" value={notes[bug.id] ?? ""} onChange={(value) => setNotes((current) => ({ ...current, [bug.id]: value }))} />
                        <Input aria-label={`${bug.itemKey} 修复证据`} placeholder="MR 或 Commit" value={evidence[bug.id] ?? ""} onChange={(value) => setEvidence((current) => ({ ...current, [bug.id]: value }))} />
                      </div>
                    ) : (
                      bug.fixNote || "—"
                    )}
                  </td>
                  <td>
                    {action && !readOnly ? (
                      <Button type="text" disabled={action === "RESOLVE" && (!notes[bug.id]?.trim() || !evidence[bug.id]?.trim())} onClick={() => onAction(bug, action, notes[bug.id], evidence[bug.id])}>
                        {bugActionLabel(action)}
                      </Button>
                    ) : (
                      "—"
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {!bugs.length && <div className={styles.empty}>暂无关联缺陷</div>}
      <Drawer width={560} title="缺陷详情" visible={Boolean(selected)} onCancel={() => setSelected(undefined)} footer={null}>
        {selected && (
          <div className={styles.detail} role="dialog" aria-label="缺陷详情">
            <header>
              <div>
                <code>{selected.itemKey}</code>
                <h3>{selected.title}</h3>
              </div>
              <Status value={selected.status} />
            </header>
            <DetailItem label="严重级别" value={selected.severity} />
            <DetailItem label="指派人" value={selected.assigneeName ?? "未指派"} />
            <DetailItem label="关联开发任务" value={selected.devTaskKey ?? "无"} />
            <div>
              <b>复现步骤</b>
              <ol>
                {selected.reproductionSteps.map((step, index) => (
                  <li key={`${index}-${step}`}>{step}</li>
                ))}
              </ol>
            </div>
            <DetailItem label="预期结果" value={selected.expectedResult} />
            <DetailItem label="实际结果" value={selected.actualResult} />
            {selected.fixNote && <DetailItem label="修复说明" value={selected.fixNote} />}
            {selected.fixEvidence.length > 0 && <DetailItem label="修复证据" value={selected.fixEvidence.join("、")} />}
          </div>
        )}
      </Drawer>
    </section>
  );
}

function DetailItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <b>{label}</b>
      <p>{value}</p>
    </div>
  );
}

function ResultStatus({ value }: { value: string }) {
  return (
    <span className={`${styles.result} ${styles[value.toLowerCase()]}`}>
      {value === "PASS" ? "✓" : value === "FAIL" ? "×" : value === "BLOCKED" ? "◷" : "—"} {testResultLabel(value)}
    </span>
  );
}
function Status({ value }: { value: string }) {
  return <span className={styles.status}>{value}</span>;
}
