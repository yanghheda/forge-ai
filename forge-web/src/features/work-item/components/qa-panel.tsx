"use client";

import { Alert, Button, Drawer, Form, Input, Select, Spin } from "@arco-design/web-react";
import { IconCheck, IconClose, IconExclamationCircle, IconPlus, IconThunderbolt } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { formatRequestError } from "@/lib/api";
import { bugActionLabel, guardHintLabel, testResultLabel } from "@/lib/labels";
import { completeTestRun, createBug, createTestCase, createTestRun, getLatestTestRun, listBugs, listTestCases, reopenTestRun, transitionBug, updateTestResult, type BugView, type TestCasePriority, type TestCaseType, type TestRunView } from "../api/work-item-api";
import styles from "./qa-panel.module.css";

type ResultChoice = "PASS" | "FAIL" | "BLOCKED" | "SKIPPED";
type QaTab = "cases" | "runs" | "bugs";

export function QaPanel({ organizationId, requirementId, onChanged }: { organizationId: number; requirementId: number; onChanged: () => Promise<unknown> }) {
  const client = useQueryClient();
  const key = ["qa", organizationId, requirementId];
  const cases = useQuery({ queryKey: [...key, "cases"], queryFn: () => listTestCases(organizationId, requirementId) });
  const run = useQuery({ queryKey: [...key, "run"], queryFn: () => getLatestTestRun(organizationId, requirementId), retry: false });
  const bugs = useQuery({ queryKey: [...key, "bugs"], queryFn: () => listBugs(organizationId, requirementId) });
  const [drawer, setDrawer] = useState(false);
  const [activeTab, setActiveTab] = useState<QaTab>("cases");
  const [form] = Form.useForm();
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
      setDrawer(false);
      form.resetFields();
      void refresh();
    },
  });
  const createRun = useMutation({ mutationFn: () => createTestRun({ organizationId, requirementId, environment: "staging" }), onSuccess: refresh });
  const update = useMutation({ mutationFn: ({ resultId, status, version }: { resultId: number; status: ResultChoice; version: number }) => updateTestResult({ organizationId, runId: run.data!.id, resultId, status, expectedVersion: version }), onSuccess: refresh });
  const complete = useMutation({ mutationFn: () => completeTestRun({ organizationId, runId: run.data!.id, expectedVersion: run.data!.version }), onSuccess: refresh });
  const reopen = useMutation({ mutationFn: () => reopenTestRun({ organizationId, runId: run.data!.id, expectedVersion: run.data!.version, reason: "重新回归" }), onSuccess: refresh });
  const createFailureBug = useMutation({
    mutationFn: (result: TestRunView["results"][number]) =>
      createBug({ organizationId, requirementId, testRunId: run.data!.id, testResultId: result.id, title: `${result.title} 失败`, severity: "MAJOR", reproductionSteps: [result.title], expectedResult: "符合 Test Case 预期", actualResult: result.actualResult || result.status }),
    onSuccess: refresh,
  });
  const moveBug = useMutation({
    mutationFn: ({ bug, action, reason, evidence }: { bug: BugView; action: BugAction; reason?: string; evidence?: string }) =>
      transitionBug({ organizationId, bugId: bug.id, action, expectedVersion: bug.version, reason: reason ?? (action === "REOPEN" ? "重新回归失败" : ""), fixEvidence: evidence ? [evidence] : [], idempotencyKey: crypto.randomUUID() }),
    onSuccess: refresh,
  });
  const error = cases.error ?? bugs.error ?? createCase.error ?? createRun.error ?? update.error ?? complete.error ?? reopen.error ?? createFailureBug.error ?? moveBug.error;
  const stats = runStats(run.data);
  const blockingBugs = (bugs.data ?? []).filter((bug) => ["BLOCKER", "CRITICAL"].includes(bug.severity) && !["VERIFIED", "CLOSED", "CANCELLED"].includes(bug.status));

  if (cases.isPending || bugs.isPending)
    return (
      <div className={styles.loading}>
        <Spin tip="正在加载 QA 数据…" />
      </div>
    );
  return (
    <div className={styles.workspace}>
      {error && <Alert type="error" content={formatRequestError(error)} />}
      <div className={styles.actions}>
        <Button disabled>导入用例</Button>
        <Button type="primary" icon={<IconPlus />} onClick={() => setDrawer(true)}>
          新建测试用例
        </Button>
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
        <button type="button" role="tab" aria-selected={activeTab === "runs"} onClick={() => setActiveTab("runs")}>
          测试运行 {run.data ? 1 : 0}
        </button>
        <button type="button" role="tab" aria-selected={activeTab === "bugs"} onClick={() => setActiveTab("bugs")}>
          缺陷 {(bugs.data ?? []).length}
        </button>
      </div>
      {activeTab === "cases" && (
        <div className={styles.mainGrid} role="tabpanel">
          <CaseTable cases={cases.data ?? []} run={run.data} busy={update.isPending} onResult={(resultId, status, version) => update.mutate({ resultId, status, version })} />
          <GatePanel run={run.data} blockingBugs={blockingBugs} />
        </div>
      )}
      {activeTab === "runs" &&
        (run.data ? (
          <div role="tabpanel">
            <QaRunView
              run={run.data}
              busy={update.isPending || createRun.isPending}
              onResult={(resultId, status, version) => update.mutate({ resultId, status, version })}
              onCreateBug={(result) => createFailureBug.mutate(result)}
              onComplete={() => complete.mutate()}
              onReopen={() => reopen.mutate()}
              onCreateNewRun={() => createRun.mutate()}
            />
          </div>
        ) : (
          <section className={styles.emptyRun} role="tabpanel">
            <strong>尚未创建测试运行</strong>
            <p>有效用例准备完成后，可创建一次 staging 测试执行。</p>
            <Button type="primary" loading={createRun.isPending} disabled={!cases.data?.length} onClick={() => createRun.mutate()}>
              创建测试执行
            </Button>
          </section>
        ))}
      {activeTab === "bugs" && (
        <div role="tabpanel">
          <BugList bugs={bugs.data ?? []} onAction={(bug, action, reason, evidence) => moveBug.mutate({ bug, action, reason, evidence })} />
        </div>
      )}
      <Drawer
        width={560}
        title="新建测试用例"
        visible={drawer}
        onCancel={() => setDrawer(false)}
        footer={
          <>
            <Button onClick={() => setDrawer(false)}>取消</Button>
            <Button type="primary" loading={createCase.isPending} onClick={() => form.submit()}>
              创建测试用例
            </Button>
          </>
        }
      >
        <Form form={form} layout="vertical" initialValues={{ priority: "P1", caseType: "FUNCTIONAL" }} onSubmit={(value) => createCase.mutate(value)}>
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

function CaseTable({ cases, run, busy, onResult }: { cases: Awaited<ReturnType<typeof listTestCases>>; run?: TestRunView; busy: boolean; onResult: (id: number, status: ResultChoice, version: number) => void }) {
  const results = new Map(run?.results.map((item) => [item.testCaseId, item]));
  return (
    <section className={styles.panel}>
      <div className={styles.toolbar}>
        <span>全部优先级</span>
        <span>全部类型</span>
        <span className={styles.search}>搜索用例编号 / 标题</span>
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
                  <td>{item.title}</td>
                  <td>
                    <Status value={item.priority} />
                  </td>
                  <td>{item.caseType === "FUNCTIONAL" ? "功能" : item.caseType === "REGRESSION" ? "回归" : "E2E"}</td>
                  <td>
                    <ResultStatus value={result?.status ?? "NOT_RUN"} />
                  </td>
                  <td>{result?.executedByName ?? (result?.executedBy ? `#${result.executedBy}` : "—")}</td>
                  <td>
                    {result && run?.status !== "COMPLETED" ? (
                      <Select
                        aria-label={`${item.title} 结果`}
                        size="small"
                        value={result.status}
                        disabled={busy}
                        onChange={(status) => status !== "NOT_RUN" && onResult(result.id, status, result.version)}
                        options={["NOT_RUN", "PASS", "FAIL", "BLOCKED", "SKIPPED"].map((value) => ({ label: testResultLabel(value), value }))}
                      />
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
      <footer>展示 {cases.length} 条用例</footer>
    </section>
  );
}

function GatePanel({ run, blockingBugs }: { run?: TestRunView; blockingBugs: BugView[] }) {
  const stats = runStats(run);
  const missing = run?.decision.missing ?? [];
  const rows = [
    { label: "服务端 QA 放行判定", passed: Boolean(run?.decision.passed), detail: run ? (run.decision.passed ? "通过" : "未通过") : "暂无运行" },
    { label: "用例全部执行", passed: !run?.results.some((item) => item.status === "NOT_RUN"), detail: run?.results.some((item) => item.status === "NOT_RUN") ? "存在未执行" : "已满足" },
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
      <Button long disabled={!run?.decision.passed}>
        推进到待发布
      </Button>
    </aside>
  );
}

export function QaRunView({
  run,
  busy,
  onCreateBug,
  onComplete,
  onReopen,
  onCreateNewRun,
}: {
  run: TestRunView;
  busy: boolean;
  onResult: (id: number, status: ResultChoice, version: number) => void;
  onCreateBug?: (result: TestRunView["results"][number]) => void;
  onComplete: () => void;
  onReopen: () => void;
  onCreateNewRun?: () => void;
}) {
  const stats = runStats(run);
  return (
    <section className={styles.runPanel}>
      <header>
        <strong>
          <IconThunderbolt /> 测试运行 RUN-{run.id}
        </strong>
        <Status value={run.status === "COMPLETED" ? "已完成" : `进行中 ${stats.rate}%`} />
      </header>
      <div className={styles.runBody}>
        <div className={styles.runProgress}>
          <i style={{ width: `${stats.rate}%` }} />
        </div>
        <dl>
          <div>
            <dt>开始时间</dt>
            <dd>{run.startedAt ? new Date(run.startedAt).toLocaleString("zh-CN") : "—"}</dd>
          </div>
          <div>
            <dt>环境</dt>
            <dd>{run.environment}</dd>
          </div>
          <div>
            <dt>触发方</dt>
            <dd>
              {run.startedByName} · {run.triggerSource === "USER" ? "手动" : "Agent"}
            </dd>
          </div>
          <div>
            <dt>用例数</dt>
            <dd>
              {run.results.length}（{stats.passed} 通过 / {stats.failed} 失败 / {stats.blocked} 阻塞）
            </dd>
          </div>
        </dl>
        <div className={styles.executionLog}>
          {run.results.map((result) => (
            <p key={result.id} className={styles[result.status.toLowerCase()]}>
              <time>{result.executedAt ? new Date(result.executedAt).toLocaleTimeString("zh-CN") : "--:--:--"}</time>
              <b>{result.status}</b>
              <span>
                CASE-{String(result.testCaseId).padStart(3, "0")} {result.title}
              </span>
              {result.actualResult && <em>{result.actualResult}</em>}
              {result.status === "FAIL" && onCreateBug && <button onClick={() => onCreateBug(result)}>创建缺陷</button>}
            </p>
          ))}
        </div>
        <div className={styles.runFooter}>
          {run.summary && (
            <span>
              通过 {run.summary.passed}/{run.summary.total}；{run.decision.passed ? "允许 QA 通过" : run.decision.missing.map(guardHintLabel).join("、")}
            </span>
          )}
          {run.status === "COMPLETED" ? (
            <div className={styles.runActions}>
              <Button disabled={busy} onClick={onReopen}>
                重新打开测试执行
              </Button>
              {onCreateNewRun && (
                <Button type="primary" loading={busy} onClick={onCreateNewRun}>
                  创建新测试执行
                </Button>
              )}
            </div>
          ) : (
            <Button type="primary" loading={busy} disabled={run.results.some((result) => result.status === "NOT_RUN")} onClick={onComplete}>
              完成测试执行
            </Button>
          )}
        </div>
      </div>
    </section>
  );
}

type BugAction = "START_FIX" | "RESOLVE" | "VERIFY" | "CLOSE" | "REOPEN";
export function BugList({ bugs, onAction }: { bugs: BugView[]; onAction: (bug: BugView, action: BugAction, reason?: string, evidence?: string) => void }) {
  const [notes, setNotes] = useState<Record<number, string>>({});
  const [evidence, setEvidence] = useState<Record<number, string>>({});
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
                    <strong className={styles.bugTitle}>{bug.title}</strong>
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
                    {bug.status === "IN_PROGRESS" ? (
                      <div className={styles.fixInputs}>
                        <Input aria-label={`${bug.itemKey} 修复说明`} placeholder="修复说明" value={notes[bug.id] ?? ""} onChange={(value) => setNotes((current) => ({ ...current, [bug.id]: value }))} />
                        <Input aria-label={`${bug.itemKey} 修复证据`} placeholder="MR 或 Commit" value={evidence[bug.id] ?? ""} onChange={(value) => setEvidence((current) => ({ ...current, [bug.id]: value }))} />
                      </div>
                    ) : (
                      bug.fixNote || "—"
                    )}
                  </td>
                  <td>
                    {action ? (
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
    </section>
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
