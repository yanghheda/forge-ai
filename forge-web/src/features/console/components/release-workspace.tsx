"use client";

import { Alert, Button, Input, Message, Select, Spin, Tag } from "@arco-design/web-react";
import { IconCheck, IconClockCircle, IconClose, IconEdit, IconPlus, IconRefresh, IconRobot, IconSafe, IconThunderbolt } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { getCurrentUser } from "@/features/auth";
import { createRelease, decideDeployment, listDeployments, listReleases, requestDeployment, runPrecheck, updateReleaseNote, type DeploymentView, type PrecheckResult, type ReleaseView } from "@/features/release";
import { getOrganizationRequirement, type OrganizationRequirement } from "@/features/work-item";
import { formatRequestError } from "@/lib/api";
import styles from "./release-workspace.module.css";

export function ReleaseWorkspace({ requirementId }: { requirementId: number }) {
  const queryClient = useQueryClient();
  const user = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
  const requirement = useQuery({ queryKey: ["requirements", requirementId], queryFn: () => getOrganizationRequirement(requirementId) });
  const releases = useQuery({ queryKey: ["releases", user.data?.organization.id], queryFn: () => listReleases(user.data!.organization.id), enabled: !!user.data });
  const release = releases.data?.find((item) => item.itemIds.includes(requirementId)) ?? null;
  const deployments = useQuery({ queryKey: ["deployments", release?.id], queryFn: () => listDeployments(user.data!.organization.id, release!.id), enabled: !!user.data && !!release });
  const [noteDraft, setNoteDraft] = useState<string | null>(null);
  const effectiveNote = noteDraft ?? release?.releaseNote ?? "";
  const refresh = () => Promise.all([queryClient.invalidateQueries({ queryKey: ["releases"] }), queryClient.invalidateQueries({ queryKey: ["deployments"] })]);
  const create = useMutation({
    mutationFn: ({ versionName, environment }: { versionName: string; environment: string }) => createRelease({ organizationId: user.data!.organization.id, versionName, environment, itemIds: [requirementId], approvalTtlMinutes: 60 }),
    onSuccess: () => void refresh(),
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const note = useMutation({
    mutationFn: () => updateReleaseNote({ organizationId: user.data!.organization.id, releaseId: release!.id, note: effectiveNote, expectedVersion: release!.version }),
    onSuccess: () => {
      setNoteDraft(null);
      void refresh();
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const precheck = useMutation({ mutationFn: () => runPrecheck({ organizationId: user.data!.organization.id, releaseId: release!.id }), onSuccess: () => void refresh(), onError: (error) => Message.error(formatRequestError(error)) });
  const deploy = useMutation({ mutationFn: () => requestDeployment({ organizationId: user.data!.organization.id, releaseId: release!.id, simulateFailure: false, idempotencyKey: crypto.randomUUID() }), onSuccess: () => void refresh(), onError: (error) => Message.error(formatRequestError(error)) });
  const decide = useMutation({
    mutationFn: ({ deployment, decision }: { deployment: DeploymentView; decision: "APPROVE" | "REJECT" }) => decideDeployment({ organizationId: user.data!.organization.id, deploymentId: deployment.id, decision, expectedVersion: deployment.version }),
    onSuccess: () => void refresh(),
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const pending = user.isPending || requirement.isPending || releases.isPending || (!!release && deployments.isPending);
  const error = user.error ?? requirement.error ?? releases.error ?? deployments.error;
  if (pending)
    return (
      <div className={styles.loading}>
        <Spin tip="正在加载发布工作区…" />
      </div>
    );
  if (error || !requirement.data || !user.data) return <Alert type="error" content={formatRequestError(error ?? new Error("无法加载发布工作区"))} />;
  return (
    <ReleaseWorkspaceView
      requirement={requirement.data}
      release={release}
      deployments={deployments.data ?? []}
      busy={create.isPending || note.isPending || precheck.isPending || deploy.isPending || decide.isPending}
      noteDraft={effectiveNote}
      onNoteChange={setNoteDraft}
      onCreate={(versionName, environment) => create.mutate({ versionName, environment })}
      onSaveNote={() => note.mutate()}
      onPrecheck={() => precheck.mutate()}
      onRequestDeployment={() => deploy.mutate()}
      onDecide={(deployment, decision) => decide.mutate({ deployment, decision })}
    />
  );
}

export function ReleaseWorkspaceView({
  requirement,
  release,
  deployments,
  busy,
  noteDraft,
  onNoteChange,
  onCreate,
  onSaveNote,
  onPrecheck,
  onRequestDeployment,
  onDecide,
}: {
  requirement: Pick<OrganizationRequirement, "itemKey" | "title" | "status">;
  release: ReleaseView | null;
  deployments: DeploymentView[];
  busy: boolean;
  noteDraft: string;
  onNoteChange: (value: string) => void;
  onCreate: (versionName: string, environment: string) => void;
  onSaveNote: () => void;
  onPrecheck: () => void;
  onRequestDeployment: () => void;
  onDecide: (deployment: DeploymentView, decision: "APPROVE" | "REJECT") => void;
}) {
  const [versionName, setVersionName] = useState("");
  const [environment, setEnvironment] = useState("staging");
  if (!release) return <CreateRelease requirement={requirement} versionName={versionName} environment={environment} busy={busy} onVersionName={setVersionName} onEnvironment={setEnvironment} onCreate={() => onCreate(versionName.trim(), environment)} />;
  const snapshot = release.latestPrecheck;
  const passed = snapshot?.checks.filter((check) => check.passed).length ?? 0;
  const deployable = snapshot?.status === "PASS" && snapshot.current;
  const currentDeployment = deployments[0];
  return (
    <div className={styles.workspace}>
      <header className={styles.head}>
        <div>
          <h1>发布管理</h1>
          <p>ReleaseAgent · 确定性预检查 / HIGH 审批 / 模拟部署</p>
        </div>
        <div className={styles.headActions}>
          <Button icon={<IconRefresh />} loading={busy} onClick={onPrecheck}>
            {snapshot ? "重新运行预检" : "运行发布预检"}
          </Button>
          <Button type="primary" icon={<IconThunderbolt />} disabled={!deployable} loading={busy} onClick={onRequestDeployment}>
            申请 HIGH 审批并模拟部署
          </Button>
        </div>
      </header>
      {!deployable && <Alert type="warning" content={snapshot ? (snapshot.current ? `预检尚有 ${snapshot.checks.length - passed} 项未通过，部署入口已锁定。` : "关键资源版本已变化，当前预检快照已失效。") : "请先完善 Release Note 并运行确定性预检。"} />}
      <ReleaseSteps release={release} deployment={currentDeployment} passed={passed} />
      <div className={styles.grid}>
        <main className={styles.stack}>
          <Panel
            title="发布单"
            extra={
              <>
                <Tag>{releaseStatusLabel(release.status)}</Tag>
                <Tag color="red">风险等级 HIGH</Tag>
              </>
            }
          >
            <dl className={styles.facts}>
              <Fact label="发布版本" value={release.versionName} mono />
              <Fact label="目标环境" value={release.environment} mono />
              <Fact label="执行模式" value="SIMULATED（不连接生产环境）" />
              <Fact label="关联需求" value={`${requirement.itemKey} · ${requirement.title}`} />
              <Fact label="需求状态" value={requirement.status} mono />
              <Fact label="聚合版本" value={`v${release.version}`} mono />
            </dl>
          </Panel>
          <Panel
            title="确定性预检查 Precheck"
            extra={
              snapshot ? (
                <>
                  <span className={styles.snapshot}>快照 #{snapshot.id}</span>
                  <Tag color={snapshot.status === "PASS" ? "green" : "orange"}>{snapshot.status === "PASS" ? "全部通过" : `${passed}/${snapshot.checks.length} 通过`}</Tag>
                </>
              ) : (
                <Tag>尚未运行</Tag>
              )
            }
          >
            {snapshot ? <PrecheckTable checks={snapshot.checks} /> : <Empty text="尚无不可变 Precheck 快照。Release Note 保存后运行检查。" />}
            <div className={styles.ruleNote}>
              <IconSafe />
              <span>结果由 forge-server 规则引擎计算；Agent 只能解释失败原因，不能决定 PASS 或绕过检查。</span>
            </div>
          </Panel>
          <Panel
            title="Release Note"
            extra={
              <Button size="small" icon={<IconEdit />} disabled={!noteDraft.trim() || noteDraft === release.releaseNote} loading={busy} onClick={onSaveNote}>
                保存说明
              </Button>
            }
          >
            <div className={styles.noteMeta}>
              <IconRobot /> 当前内容来自 Release 聚合，可由人工或 ReleaseAgent 编辑
            </div>
            <Input.TextArea aria-label="Release Note" value={noteDraft} onChange={onNoteChange} autoSize={{ minRows: 10, maxRows: 22 }} placeholder="填写变更概述、功能、修复、已知问题和回滚说明" />
          </Panel>
        </main>
        <aside className={styles.stack}>
          <ApprovalPanel deployment={currentDeployment} busy={busy} onDecide={onDecide} />
          <Panel title="发布风险">
            <Risk label="执行动作" value="HIGH" danger />
            <Risk label="部署模式" value="SIMULATED" />
            <Risk label="审批 TTL" value={currentDeployment ? formatDate(currentDeployment.approvalExpiresAt) : "申请时冻结"} />
            <Risk label="资源快照" value={snapshot?.current ? "当前有效" : "未冻结或已变化"} danger={!snapshot?.current} />
          </Panel>
          <Panel title="ReleaseAgent" extra={<Tag color={snapshot?.status === "PASS" ? "green" : "orange"}>{snapshot?.status === "PASS" ? "等待审批" : "等待门禁"}</Tag>}>
            <p className={styles.agentLine}>最近动作：{snapshot ? `读取 Precheck #${snapshot.id}` : "等待首次 Precheck"}</p>
            <p className={styles.agentLine}>权限边界：只读预检结果、生成或编辑 Note；不能执行部署。</p>
          </Panel>
          <Panel title="模拟部署记录" extra={<Tag color="orange">SIMULATED</Tag>}>
            {deployments.length ? (
              deployments.map((item) => (
                <div className={styles.deployment} key={item.id}>
                  <span className={styles.dot} />
                  <div>
                    <b>Deployment #{item.id}</b>
                    <p>
                      {deploymentStatusLabel(item.status)} · {item.resultSummary ?? "等待后续动作"}
                    </p>
                  </div>
                </div>
              ))
            ) : (
              <Empty text="尚无模拟部署记录。" />
            )}
          </Panel>
        </aside>
      </div>
    </div>
  );
}

function CreateRelease({
  requirement,
  versionName,
  environment,
  busy,
  onVersionName,
  onEnvironment,
  onCreate,
}: {
  requirement: Pick<OrganizationRequirement, "itemKey" | "title" | "status">;
  versionName: string;
  environment: string;
  busy: boolean;
  onVersionName: (value: string) => void;
  onEnvironment: (value: string) => void;
  onCreate: () => void;
}) {
  return (
    <div className={styles.workspace}>
      <header className={styles.head}>
        <div>
          <h1>发布管理</h1>
          <p>
            {requirement.itemKey} · {requirement.title}
          </p>
        </div>
      </header>
      <Panel title="创建 Release Candidate" extra={<Tag color="orange">SIMULATED</Tag>}>
        <div className={styles.create}>
          <div className={styles.createIcon}>
            <IconPlus />
          </div>
          <h2>准备本需求的发布候选版本</h2>
          <p>候选版本将聚合当前 Requirement 的 CI、QA、Bug 与 Release Note，并由服务端运行六项确定性检查。</p>
          <label>
            <span>版本号</span>
            <Input aria-label="发布版本号" value={versionName} onChange={onVersionName} placeholder="v0.2.0-rc1" />
          </label>
          <label>
            <span>目标环境</span>
            <Select
              aria-label="目标环境"
              value={environment}
              onChange={onEnvironment}
              options={[
                { label: "staging", value: "staging" },
                { label: "production（仍为模拟）", value: "production" },
              ]}
            />
          </label>
          <Alert type="info" content="本项目只执行 SIMULATED 模式，不会连接或改变生产环境。" />
          <Button type="primary" loading={busy} disabled={!versionName.trim()} onClick={onCreate}>
            创建发布候选版本
          </Button>
        </div>
      </Panel>
    </div>
  );
}

function ReleaseSteps({ release, deployment, passed }: { release: ReleaseView; deployment?: DeploymentView; passed: number }) {
  const steps = [
    { title: "发布准备", done: !!release.releaseNote, desc: release.releaseNote ? "Release Note 已保存" : "等待 Release Note" },
    { title: "预检查", done: release.latestPrecheck?.status === "PASS", desc: release.latestPrecheck ? `${passed}/${release.latestPrecheck.checks.length} 项通过` : "等待运行" },
    { title: "HIGH 审批", done: ["APPROVED", "DEPLOYING", "SUCCEEDED"].includes(deployment?.status ?? ""), desc: deployment ? deploymentStatusLabel(deployment.status) : "等待预检通过" },
    { title: "模拟部署", done: deployment?.status === "SUCCEEDED", desc: "SIMULATED" },
  ];
  return (
    <section className={styles.steps}>
      {steps.map((step, index) => (
        <div className={step.done ? styles.stepDone : styles.step} key={step.title}>
          <span>{step.done ? <IconCheck /> : index + 1}</span>
          <div>
            <b>{step.title}</b>
            <small>{step.desc}</small>
          </div>
        </div>
      ))}
    </section>
  );
}

const ruleLabels: Record<PrecheckResult["rule"], [string, string]> = {
  WORK_ITEMS_READY: ["工作项已就绪", "关联 Requirement 均为 READY_FOR_RELEASE"],
  PIPELINE_GREEN: ["Pipeline 全绿", "最新 MR HEAD 的 Pipeline 成功"],
  QA_PASSED: ["QA 门禁通过", "最新有效 Test Run 完成且通过"],
  NO_BLOCKING_BUGS: ["无阻断缺陷", "不存在未关闭 BLOCKER / CRITICAL Bug"],
  ARTIFACTS_PRESENT: ["发布材料齐全", "Release Note 与必要文档存在"],
  APPROVAL_POLICY: ["审批策略有效", "审批人可用且 TTL 配置合法"],
};
function PrecheckTable({ checks }: { checks: PrecheckResult[] }) {
  return (
    <div className={styles.tableWrap}>
      <table>
        <thead>
          <tr>
            <th>检查项</th>
            <th>Server 判定规则</th>
            <th>状态</th>
            <th>说明</th>
          </tr>
        </thead>
        <tbody>
          {checks.map((check) => (
            <tr key={check.rule}>
              <td>
                <b>{ruleLabels[check.rule][0]}</b>
                <code>{check.rule}</code>
              </td>
              <td>{ruleLabels[check.rule][1]}</td>
              <td>
                <Tag color={check.passed ? "green" : "red"}>
                  {check.passed ? <IconCheck /> : <IconClose />}
                  {check.passed ? "通过" : "未通过"}
                </Tag>
              </td>
              <td>{check.details.length ? check.details.join(" · ") : "规则条件已满足"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
function ApprovalPanel({ deployment, busy, onDecide }: { deployment?: DeploymentView; busy: boolean; onDecide: (deployment: DeploymentView, decision: "APPROVE" | "REJECT") => void }) {
  return (
    <Panel title="HIGH 风险审批" extra={<Tag color="red">HIGH</Tag>}>
      <Alert type="warning" content="审批冻结 Release version 与 PASS Precheck；过期或资源变化后自动失效。" />
      {deployment ? (
        <>
          <div className={styles.approval}>
            <IconClockCircle />
            <div>
              <b>Deployment #{deployment.id}</b>
              <p>
                {deploymentStatusLabel(deployment.status)} · 有效期至 {formatDate(deployment.approvalExpiresAt)}
              </p>
            </div>
          </div>
          {deployment.status === "PENDING_APPROVAL" && (
            <div className={styles.approvalActions}>
              <Button type="primary" loading={busy} onClick={() => onDecide(deployment, "APPROVE")}>
                批准
              </Button>
              <Button status="danger" loading={busy} onClick={() => onDecide(deployment, "REJECT")}>
                拒绝
              </Button>
            </div>
          )}
        </>
      ) : (
        <Empty text="Precheck 通过后可申请 HIGH 审批。" />
      )}
    </Panel>
  );
}
function Panel({ title, extra, children }: { title: string; extra?: React.ReactNode; children: React.ReactNode }) {
  return (
    <section className={styles.panel}>
      <header>
        <strong>{title}</strong>
        <div>{extra}</div>
      </header>
      <div className={styles.panelBody}>{children}</div>
    </section>
  );
}
function Fact({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd className={mono ? styles.mono : ""}>{value}</dd>
    </div>
  );
}
function Risk({ label, value, danger }: { label: string; value: string; danger?: boolean }) {
  return (
    <div className={styles.risk}>
      <span>{label}</span>
      <Tag color={danger ? "red" : "blue"}>{value}</Tag>
    </div>
  );
}
function Empty({ text }: { text: string }) {
  return <p className={styles.empty}>{text}</p>;
}
function formatDate(value: string) {
  return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}
function releaseStatusLabel(value: string) {
  return ({ DRAFT: "草稿", PRECHECKED: "已预检", READY_FOR_APPROVAL: "待审批", APPROVED: "已批准", DEPLOYING: "部署中", RELEASED: "已发布", FAILED: "失败" } as Record<string, string>)[value] ?? value;
}
function deploymentStatusLabel(value: string) {
  return ({ PENDING_APPROVAL: "等待审批", APPROVED: "已批准", DEPLOYING: "模拟部署中", SUCCEEDED: "模拟部署成功", FAILED: "模拟部署失败", REJECTED: "已拒绝", EXPIRED: "已过期" } as Record<string, string>)[value] ?? value;
}
