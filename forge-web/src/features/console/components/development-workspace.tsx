"use client";

import { Alert, Button, Drawer, Input, Message, Space, Spin, Tag } from "@arco-design/web-react";
import { IconBranch, IconLaunch, IconLink, IconRefresh, IconSafe, IconThunderbolt } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { triggerPipeline } from "@/features/gitlab";
import { completeDevTask, createDevTask, getDevelopmentSummary, getRequirementWorkflow, startDevelopment, transitionRequirementWorkflow, type DevelopmentQaSummary, type DevelopmentQaTask, type RequirementWorkflow } from "@/features/work-item";
import { formatRequestError } from "@/lib/api";
import { ChainNode, Empty, Guard, Panel, Stat, shortSha, statusLabel, syncText } from "./development-workspace-parts";
import styles from "./development-workspace.module.css";

export function DevelopmentWorkspace({ requirementId }: { requirementId: number }) {
  const queryClient = useQueryClient();
  const summaryKey = ["development-summary", requirementId];
  const workflowKey = ["requirements", requirementId, "workflow"];
  const summary = useQuery({ queryKey: summaryKey, queryFn: () => getDevelopmentSummary(0, requirementId), refetchInterval: 3_000 });
  const workflow = useQuery({ queryKey: workflowKey, queryFn: () => getRequirementWorkflow(requirementId) });
  const refresh = async () => {
    await Promise.all([queryClient.invalidateQueries({ queryKey: summaryKey }), queryClient.invalidateQueries({ queryKey: workflowKey }), queryClient.invalidateQueries({ queryKey: ["requirements", requirementId] })]);
  };
  const create = useMutation({
    mutationFn: ({ title, description }: { title: string; description: string }) => createDevTask({ organizationId: 0, requirementId, title, description }),
    onSuccess: refresh,
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const start = useMutation({ mutationFn: (taskId: number) => startDevelopment({ organizationId: 0, taskId, idempotencyKey: crypto.randomUUID() }), onSuccess: refresh, onError: (error) => Message.error(formatRequestError(error)) });
  const pipeline = useMutation({
    mutationFn: ({ taskId, ref }: { taskId: number; ref: string }) => triggerPipeline({ devTaskId: taskId, ref }),
    onSuccess: refresh,
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const complete = useMutation({ mutationFn: ({ taskId, version }: { taskId: number; version: number }) => completeDevTask({ organizationId: 0, taskId, expectedVersion: version }), onSuccess: refresh, onError: (error) => Message.error(formatRequestError(error)) });
  const submit = useMutation({ mutationFn: () => transitionRequirementWorkflow(requirementId, "SUBMIT_FOR_QA", workflow.data!.version), onSuccess: refresh, onError: (error) => Message.error(formatRequestError(error)) });

  if (summary.isPending || workflow.isPending)
    return (
      <div className={styles.loading}>
        <Spin tip="正在加载开发工作区…" />
      </div>
    );
  if (!summary.data || !workflow.data || summary.isError || workflow.isError) return <Alert type="error" content={formatRequestError(summary.error ?? workflow.error)} />;
  const pending: DevelopmentPendingAction | undefined = create.isPending
    ? { action: "create" }
    : pipeline.isPending
      ? { action: "pipeline", taskId: pipeline.variables?.taskId }
      : start.isPending
        ? { action: "start", taskId: start.variables }
        : complete.isPending
          ? { action: "complete", taskId: complete.variables?.taskId }
          : submit.isPending
            ? { action: "submit" }
            : undefined;
  return (
    <DevelopmentWorkspaceView
      summary={summary.data}
      workflow={workflow.data}
      pending={pending}
      onCreate={(title, description) => create.mutateAsync({ title, description }).then(() => undefined)}
      onStart={(taskId) => start.mutate(taskId)}
      onTriggerPipeline={(taskId, ref) => pipeline.mutate({ taskId, ref })}
      onComplete={(taskId, version) => complete.mutate({ taskId, version })}
      onRefresh={() => void refresh()}
      onSubmitQa={() => submit.mutate()}
    />
  );
}

/* 当前进行中的写操作；taskId 用于只让对应 Dev Task 卡片的按钮进入加载态。 */
export type DevelopmentPendingAction = { action: "create" | "start" | "pipeline" | "complete" | "submit"; taskId?: number };

export function DevelopmentWorkspaceView({
  summary,
  workflow,
  pending,
  onCreate,
  onStart,
  onTriggerPipeline,
  onComplete,
  onRefresh,
  onSubmitQa,
}: {
  summary: DevelopmentQaSummary;
  workflow: RequirementWorkflow;
  pending?: DevelopmentPendingAction;
  onCreate: (title: string, description: string) => Promise<void>;
  onStart: (taskId: number) => void;
  onTriggerPipeline: (taskId: number, ref: string) => void;
  onComplete: (taskId: number, version: number) => void;
  onRefresh: () => void;
  onSubmitQa: () => void;
}) {
  const [taskTitle, setTaskTitle] = useState("");
  const [taskDescription, setTaskDescription] = useState("");
  const [creatorOpen, setCreatorOpen] = useState(false);
  const tasks = summary.tasks;
  const completed = tasks.filter((task) => task.status === "DONE").length;
  const canCreateTask = tasks.every((task) => task.status === "TODO");
  const mrCount = tasks.filter((task) => task.mergeRequestId != null).length;
  const ciPassed = tasks.filter((task) => checkTaskCi(task).ok).length;
  const pipelineOk = !summary.ciRequired || (tasks.length > 0 && ciPassed === tasks.length);
  const missing = workflow.guardHints.SUBMIT_FOR_QA ?? [];
  const actionAvailable = workflow.availableActions.includes("SUBMIT_FOR_QA");
  const guardPassed = tasks.length > 0 && completed === tasks.length && pipelineOk && (summary.repositoryConfigured || !summary.ciRequired) && missing.length === 0;
  const canSubmit = actionAvailable && guardPassed;
  const isBusy = (action: DevelopmentPendingAction["action"], taskId?: number) => pending?.action === action && pending.taskId === taskId;

  const submitTask = async () => {
    await onCreate(taskTitle.trim(), taskDescription.trim());
    setTaskTitle("");
    setTaskDescription("");
    setCreatorOpen(false);
  };

  return (
    <>
      <div className={styles.workspace}>
        <header className={styles.head}>
          <div>
            <h1>开发</h1>
            <p>DevAgent · GitLab 分支 / MR / Pipeline 协同</p>
          </div>
          <div className={styles.actions}>
            <Button icon={<IconRefresh />} onClick={onRefresh}>
              同步状态
            </Button>
          </div>
        </header>

        <section className={styles.stats} aria-label="开发统计">
          <Stat icon={<IconLink />} tone={mrCount ? "blue" : "gray"} label="合并请求" value={`${mrCount} / ${tasks.length}`} foot={tasks.length > 0 && mrCount === tasks.length ? "每个 Dev Task 均已创建 MR" : "已创建 MR / 全部 Dev Task"} />
          <Stat
            icon={<IconThunderbolt />}
            tone={pipelineOk ? "green" : "blue"}
            label="Pipeline"
            value={`${ciPassed} / ${tasks.length}`}
            foot={summary.ciRequired ? (tasks.length > 0 && ciPassed === tasks.length ? "全部对应 MR 当前 HEAD" : "MR 当前 HEAD 通过 / 全部 Dev Task") : "当前项目策略不要求 CI"}
          />
          <Stat icon={<IconBranch />} tone="gray" label="开发任务" value={`${completed} / ${tasks.length}`} foot="已完成 / 全部 Dev Task" />
          <Stat icon={<IconSafe />} tone={missing.length ? "red" : "green"} label="QA 门禁缺项" value={String(missing.length)} foot={missing.length ? "需全部解决才能推进 QA" : "已满足当前服务端规则"} />
        </section>

        <div className={styles.columns}>
          <main className={styles.stack}>
            <Panel
              title="Dev Task 交付链路"
              icon={<IconBranch />}
              extra={
                <Space>
                  <span className={styles.muted}>
                    {completed}/{tasks.length} 已完成
                  </span>
                  {canCreateTask && (
                    <Button type="primary" size="small" onClick={() => setCreatorOpen(true)}>
                      新建 Dev Task
                    </Button>
                  )}
                </Space>
              }
            >
              <div className={styles.deliveryList}>
                {tasks.map((task) => {
                  const check = checkTaskCi(task);
                  const pipelineStatus = task.pipelineStatus?.toLowerCase();
                  const stale = Boolean(task.pipelineId && task.mergeRequestHeadSha !== task.pipelineCommitSha);
                  const pipelineTone = !task.pipelineId ? "idle" : check.ok ? "ok" : pipelineStatus === "failed" || pipelineStatus === "canceled" ? "fail" : "run";
                  const mrMerged = task.mergeRequestState?.toLowerCase() === "merged";
                  return (
                    <article className={styles.deliveryCard} key={task.id}>
                      <header className={styles.deliveryHead}>
                        <div>
                          <code>{task.itemKey}</code>
                          <strong>{task.title}</strong>
                        </div>
                        <Tag color={task.status === "DONE" ? "green" : task.status === "TODO" ? "gray" : "arcoblue"}>{statusLabel(task.status)}</Tag>
                      </header>
                      {task.branchName ? (
                        <div className={styles.chain}>
                          <ChainNode icon={<IconBranch />} title={task.branchName} caption="源分支" tone="run" />
                          <span className={styles.line} />
                          <ChainNode icon={<IconLink />} title={task.mergeRequestId ? `!${task.mergeRequestId}` : "尚未创建"} caption={mergeStateLabel(task)} tone={!task.mergeRequestId ? "idle" : mrMerged ? "ok" : "run"} />
                          <span className={styles.line} />
                          <ChainNode
                            icon={<IconThunderbolt />}
                            title={task.pipelineId ? `Pipeline #${task.pipelineId}` : "尚未触发"}
                            caption={task.pipelineId ? `${task.pipelineStatus ? statusLabel(task.pipelineStatus) : "状态同步中"} · ${stale ? "不是 MR 当前 HEAD" : "对应 MR 当前 HEAD"}` : "触发后在此展示运行结果"}
                            tone={pipelineTone}
                          />
                        </div>
                      ) : (
                        <Empty text="尚未启动开发，启动后自动创建源分支与合并请求。" />
                      )}
                      <footer className={styles.deliveryFoot}>
                        <span className={styles.muted}>{task.mergeRequestId ? `MR HEAD ${shortSha(task.mergeRequestHeadSha)} · ${syncText(task.pipelineLastSyncedAt)}` : "尚无 MR 快照"}</span>
                        <Space>
                          {task.mergeRequestUrl && (
                            <Button size="small" href={task.mergeRequestUrl} target="_blank" icon={<IconLaunch />}>
                              查看 MR
                            </Button>
                          )}
                          {task.status === "TODO" && (
                            <Button size="small" loading={isBusy("start", task.id)} onClick={() => onStart(task.id)}>
                              启动开发
                            </Button>
                          )}
                          {(task.status === "IN_PROGRESS" || task.status === "DONE") && task.branchName && (
                            <>
                              <Button size="small" loading={isBusy("pipeline", task.id)} onClick={() => onTriggerPipeline(task.id, task.branchName!)}>
                                {task.pipelineId ? "重新触发 Pipeline" : "触发 Pipeline"}
                              </Button>
                              {task.status === "IN_PROGRESS" && (
                                <Button size="small" type="primary" loading={isBusy("complete", task.id)} disabled={summary.ciRequired && !check.ok} title={summary.ciRequired && !check.ok ? `Pipeline 通过后才能完成任务：${check.detail}` : undefined} onClick={() => onComplete(task.id, task.version)}>
                                  完成任务
                                </Button>
                              )}
                            </>
                          )}
                        </Space>
                      </footer>
                    </article>
                  );
                })}
              </div>
              {!tasks.length && <Empty text="尚未创建研发任务。" />}
            </Panel>
          </main>

          <aside className={styles.stack}>
            <Panel title="进入 QA 门禁（确定性检查）" icon={<IconSafe />} extra={<Tag color={guardPassed ? "green" : "red"}>{guardPassed ? "已通过" : "未通过"}</Tag>}>
              <Guard ok={tasks.length > 0 && completed === tasks.length} label="Dev Task 全部完成" detail={`${completed} / ${tasks.length}`} />
              <Guard ok={summary.repositoryConfigured || !summary.ciRequired} label="GitLab 仓库已配置" detail={summary.repositoryConfigured ? "已绑定 ACTIVE 仓库" : summary.ciRequired ? "CI 策略要求仓库" : "CI 非必需"} />
              {tasks.map((task) => {
                const check = checkTaskCi(task);
                return <Guard key={task.id} ok={!summary.ciRequired || check.ok} label={`${task.itemKey} · ${task.title}`} detail={summary.ciRequired ? check.detail : "CI 非必需"} />;
              })}
              {!tasks.length && <Guard ok={false} label="每个 Dev Task 的 MR 当前 HEAD 的 Pipeline 成功" detail="尚无 Dev Task" />}
              {missing.length > 0 && (
                <Alert
                  type="error"
                  title="门禁未通过，无法推进到 QA"
                  content={
                    <span>
                      {missing.map((item) => (
                        <code className={styles.missing} key={item}>
                          {item}
                        </code>
                      ))}
                    </span>
                  }
                />
              )}
              {guardPassed && !actionAvailable && <Alert type="warning" content="当前账号无推进权限，或需求已不处于开发中。" />}
              <div className={styles.guardFoot}>
                <span>检查结果由 forge-server 确定性计算，Agent 不能自行放行。</span>
                <Button type="primary" disabled={!canSubmit} loading={isBusy("submit")} onClick={onSubmitQa}>
                  推进到 QA
                </Button>
              </div>
            </Panel>
            <Panel title="交付安全边界" icon={<IconSafe />}>
              <p className={styles.policy}>DevAgent 只通过受控 Server Tool API 操作；GitLab 凭据与业务状态均由 forge-server 管理。</p>
              <div className={styles.chips}>
                <Tag>Server Tool API</Tag>
                <Tag>审计与权限校验</Tag>
                <Tag>{summary.ciRequired ? "CI 必需" : "CI 可选"}</Tag>
                <Tag>MR 目标分支由 GitLab 项目策略决定</Tag>
              </div>
            </Panel>
          </aside>
        </div>
      </div>
      <Drawer
        visible={creatorOpen}
        width={480}
        title="新建 Dev Task"
        onCancel={() => setCreatorOpen(false)}
        footer={
          <>
            <Button onClick={() => setCreatorOpen(false)}>取消</Button>
            <Button type="primary" loading={isBusy("create")} disabled={!taskTitle.trim()} onClick={() => void submitTask()}>
              创建 Dev Task
            </Button>
          </>
        }
      >
        <Space direction="vertical" size="large" style={{ width: "100%" }}>
          <label>
            <span className={styles.fieldLabel}>任务标题</span>
            <Input aria-label="Dev Task 标题" value={taskTitle} onChange={setTaskTitle} placeholder="输入 Dev Task 标题" maxLength={255} />
          </label>
          <label>
            <span className={styles.fieldLabel}>任务说明</span>
            <Input.TextArea aria-label="Dev Task 说明" value={taskDescription} onChange={setTaskDescription} placeholder="实现范围与约束（可选）" autoSize={{ minRows: 5, maxRows: 12 }} maxLength={20000} showWordLimit />
          </label>
        </Space>
      </Drawer>
    </>
  );
}

/* MR 合并状态文案；完成任务要求该 Dev Task 的 MR 已合并。 */
function mergeStateLabel(task: DevelopmentQaTask) {
  const state = task.mergeRequestState?.toLowerCase();
  if (!state) return "合并请求";
  if (state === "merged") return "合并请求 · 已合并";
  if (state === "closed") return "合并请求 · 已关闭";
  return "合并请求 · 待合并";
}

/* 与 forge-server DevelopmentQaGuard 的逐任务 CI 规则保持一致：每个 Dev Task 都要有自己的 MR、Pipeline，且 Pipeline 必须对应 MR 当前 HEAD 并成功。 */
function checkTaskCi(task: DevelopmentQaTask) {
  if (!task.mergeRequestId) return { ok: false, detail: "尚未创建合并请求" };
  if (!task.pipelineId) return { ok: false, detail: "尚未触发 Pipeline" };
  if (task.mergeRequestHeadSha !== task.pipelineCommitSha) return { ok: false, detail: `Pipeline #${task.pipelineId} 不是 MR 当前 HEAD 的运行结果` };
  const status = task.pipelineStatus?.toLowerCase();
  if (status === "success") return { ok: true, detail: `Pipeline #${task.pipelineId} 成功且对应 MR 当前 HEAD` };
  if (status === "running" || status === "pending" || status === "created") return { ok: false, detail: `Pipeline #${task.pipelineId} 运行中` };
  return { ok: false, detail: `Pipeline #${task.pipelineId} ${task.pipelineStatus ? statusLabel(task.pipelineStatus) : "状态未知"}` };
}
