"use client";

import { Alert, Button, Card, Input, Space, Spin, Typography } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { getCurrentUser } from "@/features/auth";
import { listProjects } from "@/features/project";
import { formatRequestError } from "@/lib/api";
import { priorityLabel, workflowActionLabel } from "@/lib/labels";
import ui from "@/components/workbench/workbench.module.css";
import { getWorkItem, listUxTasks, transitionRequirement, type WorkItemDetail, type WorkItemSummary, type WorkflowAction } from "../api/work-item-api";

export function UxTaskList({ organizationSlug, organizationKey, tasks }: { organizationSlug: string; organizationKey: string; tasks: WorkItemSummary[] }) {
  if (tasks.length === 0) return <Typography.Text>暂无待处理 UX 任务。</Typography.Text>;
  return (
    <div className={ui.list}>
      {tasks.map((task) => (
        <Link aria-label={`${task.itemKey} · ${task.title} · ${task.status}`} className={ui.listItem} key={task.id} href={`/w/${organizationSlug}/p/${organizationKey}/ux/${task.id}`}>
          <span className={ui.itemMain}>
            <span className={ui.itemTitle}>{task.title}</span>
            <span className={ui.itemMeta}>
              {task.itemKey} · {priorityLabel(task.priority)}
            </span>
          </span>
          <Typography.Text type="secondary">{task.status}</Typography.Text>
        </Link>
      ))}
    </div>
  );
}

export function UxOrganization({ organizationSlug, organizationKey }: { organizationSlug: string; organizationKey: string }) {
  const user = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
  const organization = user.data?.organizations.find((item) => item.slug === organizationSlug);
  const projects = useQuery({
    queryKey: ["projects", organization?.id],
    queryFn: () => listProjects(organization!.id),
    enabled: !!organization,
  });
  const project = projects.data?.find((item) => item.key === organizationKey);
  const tasks = useQuery({
    queryKey: ["work-items", project?.id, "UX_TASK"],
    queryFn: () => listUxTasks(organization!.id, project!.id),
    enabled: !!organization && !!project,
  });
  if (user.isPending || projects.isPending || tasks.isPending) return <Spin tip="正在加载 UX 队列…" />;
  if (!organization || !project) return <Alert type="error" content="项目不存在或无 UX 读取权限。" />;
  return (
    <section className={ui.page}>
      <header className={ui.pageHeader}>
        <div>
          <span className={ui.eyebrow}>设计交付</span>
          <h1>UX 工作台</h1>
          <p>处理设计任务，提交 UX 交付物并跟进评审。</p>
        </div>
      </header>
      <Card title="UX 任务" className={ui.panel}>
        <UxTaskList organizationSlug={organizationSlug} organizationKey={organizationKey} tasks={tasks.data?.items ?? []} />
      </Card>
    </section>
  );
}

export function UxTaskDetailView({ task, busy, reason = "", error, onReasonChange = () => undefined, onTransition }: { task: WorkItemDetail; busy: boolean; reason?: string; error?: unknown; onReasonChange?: (value: string) => void; onTransition: (action: WorkflowAction) => void }) {
  const needsReason = task.availableActions.includes("REJECT");
  return (
    <Card title={`${task.itemKey} · ${task.title}`} className={ui.panel}>
      <div className={ui.content}>
        <Space direction="vertical" style={{ width: "100%" }}>
          <Typography.Text>
            {task.status} · 版本 {task.version}
          </Typography.Text>
          {task.description && <Typography.Paragraph>{task.description}</Typography.Paragraph>}
          {!!error && <Alert type="error" content={formatRequestError(error)} />}
          {needsReason && <Input.TextArea aria-label="评审意见" value={reason} onChange={onReasonChange} placeholder="退回时请填写修改意见" />}
          <Space wrap>
            {task.availableActions.map((action) => (
              <Button key={action} type={action === "START" || action === "SUBMIT_REVIEW" ? "primary" : "secondary"} loading={busy} disabled={action === "REJECT" && !reason.trim()} onClick={() => onTransition(action)}>
                {workflowActionLabel(action)}
              </Button>
            ))}
          </Space>
          {task.availableActions.length === 0 && <Typography.Text type="secondary">当前状态没有可执行动作，请返回队列或等待评审。</Typography.Text>}
        </Space>
      </div>
    </Card>
  );
}

export function UxTaskRoute({ organizationSlug, organizationKey, workItemId }: { organizationSlug: string; organizationKey: string; workItemId: number }) {
  const queryClient = useQueryClient();
  const [reason, setReason] = useState("");
  const user = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
  const organization = user.data?.organizations.find((item) => item.slug === organizationSlug);
  const projects = useQuery({
    queryKey: ["projects", organization?.id],
    queryFn: () => listProjects(organization!.id),
    enabled: !!organization,
  });
  const project = projects.data?.find((item) => item.key === organizationKey);
  const task = useQuery({
    queryKey: ["work-item", workItemId],
    queryFn: () => getWorkItem(organization!.id, project!.id, workItemId),
    enabled: !!organization && !!project,
  });
  const transition = useMutation({
    mutationFn: (action: WorkflowAction) => transitionRequirement(organization!.id, project!.id, workItemId, action, task.data!.version, action === "REJECT" ? reason : undefined),
    onSuccess: () => {
      setReason("");
      void queryClient.invalidateQueries({ queryKey: ["work-item", workItemId] });
      void queryClient.invalidateQueries({ queryKey: ["work-items", project?.id, "UX_TASK"] });
    },
  });

  if (user.isPending || projects.isPending || task.isPending) return <Spin tip="正在加载 UX 任务…" />;
  if (!organization || !project || !task.data || task.data.type !== "UX_TASK") return <Alert type="error" content="UX 任务不存在或当前账户无权访问。" />;
  const error = transition.error;
  return (
    <section className={ui.page}>
      <header className={ui.pageHeader}>
        <div>
          <span className={ui.eyebrow}>UX 任务</span>
          <h1>{task.data.itemKey}</h1>
          <p>{task.data.title}</p>
        </div>
      </header>
      <Link href={`/w/${organizationSlug}/p/${organizationKey}/ux`}>返回 UX 工作台</Link>
      <UxTaskDetailView task={task.data} busy={transition.isPending} reason={reason} error={error} onReasonChange={setReason} onTransition={(action) => transition.mutate(action)} />
    </section>
  );
}
