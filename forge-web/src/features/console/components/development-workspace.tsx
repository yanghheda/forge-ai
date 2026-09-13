"use client";

import { Alert, Button, Drawer, Input, Message, Space, Spin, Tag } from "@arco-design/web-react";
import { IconBranch, IconCheck, IconClockCircle, IconCode, IconLink, IconRefresh, IconRobot, IconSafe, IconThunderbolt } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { triggerPipeline } from "@/features/gitlab";
import { completeDevTask, createDevTask, getDevelopmentSummary, getRequirementWorkflow, startDevelopment, transitionRequirementWorkflow, type DevelopmentQaSummary, type RequirementWorkflow } from "@/features/work-item";
import { formatRequestError } from "@/lib/api";
import { Branch, Empty, Fact, Guard, Metric, Panel, Stat, shortSha, statusLabel, syncText } from "./development-workspace-parts";
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
  return (
    <DevelopmentWorkspaceView
      summary={summary.data}
      workflow={workflow.data}
      busy={create.isPending || start.isPending || pipeline.isPending || complete.isPending || submit.isPending}
      onCreate={(title, description) => create.mutateAsync({ title, description }).then(() => undefined)}
      onStart={(taskId) => start.mutate(taskId)}
      onTriggerPipeline={(taskId, ref) => pipeline.mutate({ taskId, ref })}
      onComplete={(taskId, version) => complete.mutate({ taskId, version })}
      onRefresh={() => void refresh()}
      onSubmitQa={() => submit.mutate()}
    />
  );
}

export function DevelopmentWorkspaceView({
  summary,
  workflow,
  busy,
  onCreate,
  onStart,
  onTriggerPipeline,
  onComplete,
  onRefresh,
  onSubmitQa,
}: {
  summary: DevelopmentQaSummary;
  workflow: RequirementWorkflow;
  busy: boolean;
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
  const current = summary.tasks.find((task) => task.mergeRequestId != null) ?? summary.tasks[0];
  const completed = summary.tasks.filter((task) => task.status === "DONE").length;
  const canCreateTask = summary.tasks.every((task) => task.status === "TODO");
  const stale = Boolean(current?.pipelineId && current.mergeRequestHeadSha !== current.pipelineCommitSha);
  const pipelineOk = !summary.ciRequired || (current?.pipelineStatus?.toLowerCase() === "success" && !stale);
  const missing = workflow.guardHints.SUBMIT_FOR_QA ?? [];
  const actionAvailable = workflow.availableActions.includes("SUBMIT_FOR_QA");
  const guardPassed = summary.tasks.length > 0 && completed === summary.tasks.length && pipelineOk && (summary.repositoryConfigured || !summary.ciRequired) && missing.length === 0;
  const canSubmit = actionAvailable && guardPassed;
  const pipelineLabel = current?.pipelineStatus ? statusLabel(current.pipelineStatus) : "暂无运行";

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
          {current?.mergeRequestUrl && (
            <Button href={current.mergeRequestUrl} target="_blank" icon={<IconLink />}>
              在 GitLab 查看
            </Button>
          )}
          <Button icon={<IconRefresh />} onClick={onRefresh}>
            同步状态
          </Button>
        </div>
      </header>

      <section className={styles.stats} aria-label="开发统计">
        <Stat icon={<IconBranch />} tone="orange" label="当前 MR" value={current?.mergeRequestId ? `!${current.mergeRequestId}` : "暂无"} foot={current?.branchName ?? "尚未创建开发分支"} />
        <Stat icon={<IconThunderbolt />} label={current?.pipelineId ? `Pipeline #${current.pipelineId}` : "Pipeline"} value={pipelineLabel} foot={stale ? "Pipeline 不属于 MR 当前 HEAD" : syncText(current?.pipelineLastSyncedAt)} />
        <Stat icon={<IconCode />} tone="gray" label="开发任务" value={`${completed} / ${summary.tasks.length}`} foot="已完成 / 全部 Dev Task" />
        <Stat icon={<IconSafe />} tone={missing.length ? "red" : "green"} label="QA 门禁缺项" value={String(missing.length)} foot={missing.length ? "需全部解决才能推进 QA" : "已满足当前服务端规则"} />
      </section>

      <div className={styles.columns}>
        <main className={styles.stack}>
          <Panel
            title="分支与合并请求"
            icon={<IconBranch />}
            extra={
              <Tag>
                <IconCode /> Agent 经 Server Tool API 操作
              </Tag>
            }
          >
            {current ? (
              <>
                <div className={styles.branchFlow}>
                  <Branch name={current.branchName ?? "尚未创建源分支"} caption="源分支" />
                  <span className={styles.line} />
                  <span className={styles.merge}>
                    <IconBranch />
                  </span>
                  <span className={styles.line} />
                  <Branch name="目标分支由 GitLab 项目策略决定" caption="受保护分支" />
                </div>
                <dl className={styles.facts}>
                  <Fact label="开发任务" value={`${current.itemKey} · ${current.title}`} />
                  <Fact label="合并请求" value={current.mergeRequestId ? `!${current.mergeRequestId}` : "尚未创建"} />
                  <Fact label="源分支" value={current.branchName ?? "—"} mono />
                  <Fact label="MR HEAD" value={shortSha(current.mergeRequestHeadSha)} mono />
                  <Fact label="更新时间" value={syncText(current.pipelineLastSyncedAt)} />
                </dl>
              </>
            ) : (
              <Empty text="尚未创建研发任务。" />
            )}
          </Panel>

          <Panel title={current?.pipelineId ? `Pipeline #${current.pipelineId}` : "Pipeline"} icon={<IconThunderbolt />} extra={<Tag color={pipelineOk ? "green" : "arcoblue"}>{pipelineLabel}</Tag>}>
            {current?.pipelineId ? (
              <div className={styles.pipeline}>
                <span className={pipelineOk ? styles.okDot : styles.runDot}>{pipelineOk ? <IconCheck /> : <IconClockCircle />}</span>
                <div>
                  <strong>{pipelineLabel}</strong>
                  <p>
                    {shortSha(current.pipelineCommitSha)} · {stale ? "不是 MR 当前 HEAD" : "对应 MR 当前 HEAD"}
                  </p>
                </div>
              </div>
            ) : (
              <Empty text="启动开发并创建 MR 后，将在这里显示真实 Pipeline 状态。" />
            )}
          </Panel>

          <Panel
            title="Dev Task"
            icon={<IconCode />}
            extra={
              <Space>
                <span className={styles.muted}>
                  {completed}/{summary.tasks.length} 已完成
                </span>
                {canCreateTask && (
                  <Button type="primary" size="small" onClick={() => setCreatorOpen(true)}>
                    新建 Dev Task
                  </Button>
                )}
              </Space>
            }
          >
            <div className={styles.tasks}>
              {summary.tasks.map((task) => (
                <div className={styles.task} key={task.id}>
                  <div>
                    <code>{task.itemKey}</code>
                    <strong>{task.title}</strong>
                    <span>{task.branchName ?? "尚无分支"}</span>
                  </div>
                  <Tag>{statusLabel(task.status)}</Tag>
                  {task.status === "TODO" && (
                    <Button loading={busy} onClick={() => onStart(task.id)}>
                      启动开发
                    </Button>
                  )}
                  {(task.status === "IN_PROGRESS" || task.status === "DONE") && task.branchName && (
                    <Space className={styles.taskActions}>
                      <Button loading={busy} onClick={() => onTriggerPipeline(task.id, task.branchName!)}>
                        {task.pipelineId ? "重新触发 Pipeline" : "触发 Pipeline"}
                      </Button>
                      {task.status === "IN_PROGRESS" && (
                        <Button
                          type="primary"
                          loading={busy}
                          disabled={summary.ciRequired && (task.pipelineStatus?.toLowerCase() !== "success" || task.mergeRequestHeadSha !== task.pipelineCommitSha)}
                          title={summary.ciRequired && task.pipelineStatus?.toLowerCase() !== "success" ? "Pipeline 成功后才能完成任务" : undefined}
                          onClick={() => onComplete(task.id, task.version)}
                        >
                          完成任务
                        </Button>
                      )}
                    </Space>
                  )}
                </div>
              ))}
            </div>
            {!summary.tasks.length && <Empty text="尚未创建研发任务。" />}
          </Panel>

          <Panel title="进入 QA 门禁（确定性检查）" icon={<IconSafe />} extra={<Tag color={guardPassed ? "green" : "red"}>{guardPassed ? "已通过" : "未通过"}</Tag>}>
            <Guard ok={summary.tasks.length > 0 && completed === summary.tasks.length} label="Dev Task 全部完成" detail={`${completed} / ${summary.tasks.length}`} />
            <Guard ok={summary.repositoryConfigured || !summary.ciRequired} label="GitLab 仓库已配置" detail={summary.repositoryConfigured ? "已绑定 ACTIVE 仓库" : summary.ciRequired ? "CI 策略要求仓库" : "CI 非必需"} />
            <Guard ok={pipelineOk} label="MR 当前 HEAD 的 Pipeline 成功" detail={pipelineLabel} />
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
              <Button type="primary" disabled={!canSubmit} loading={busy} onClick={onSubmitQa}>
                推进到 QA
              </Button>
            </div>
          </Panel>
        </main>

        <aside className={styles.stack}>
          <Panel title="DevAgent" icon={<IconRobot />} extra={<Tag color="arcoblue">● 运行中</Tag>}>
            <p className={styles.role}>DEVELOPER 角色 · Requirement #{summary.requirementId}</p>
            <Alert type="info" content={missing.length ? `当前动作：等待 ${missing.length} 项服务端门禁条件满足。` : "当前动作：开发交付事实已同步，可提交进入 QA。"} />
            <div className={styles.metrics}>
              <Metric value={summary.tasks.length} label="Dev Task" />
              <Metric value={completed} label="已完成" />
              <Metric value={current?.mergeRequestId ? 1 : 0} label="Merge Request" />
              <Metric value={current?.pipelineId ? 1 : 0} label="Pipeline" />
            </div>
          </Panel>
          <Panel title="交付安全边界" icon={<IconSafe />}>
            <p className={styles.policy}>DevAgent 只通过受控 Server Tool API 操作；GitLab 凭据与业务状态均由 forge-server 管理。</p>
            <div className={styles.chips}>
              <Tag>Server Tool API</Tag>
              <Tag>审计与权限校验</Tag>
              <Tag>{summary.ciRequired ? "CI 必需" : "CI 可选"}</Tag>
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
            <Button type="primary" loading={busy} disabled={!taskTitle.trim()} onClick={() => void submitTask()}>
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
