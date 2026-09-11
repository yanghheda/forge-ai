"use client";

import { Alert, Button, Card, Space, Tag, Typography } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { formatRequestError } from "@/lib/api";

import { completeDevTask, getDevelopmentSummary, startDevelopment, type DevelopmentQaSummary } from "../api/work-item-api";

export function DevelopmentPanel({ organizationId, requirementId, onChanged }: { organizationId: number; requirementId: number; onChanged: () => Promise<unknown> }) {
  const queryClient = useQueryClient();
  const queryKey = ["development-summary", organizationId, requirementId];
  const summary = useQuery({
    queryKey,
    queryFn: () => getDevelopmentSummary(organizationId, requirementId),
    refetchInterval: 3_000,
  });
  const refresh = async () => {
    await Promise.all([queryClient.invalidateQueries({ queryKey }), onChanged()]);
  };
  const start = useMutation({
    mutationFn: (taskId: number) =>
      startDevelopment({
        organizationId,
        taskId,
        idempotencyKey: crypto.randomUUID(),
      }),
    onSuccess: refresh,
  });
  const complete = useMutation({
    mutationFn: ({ taskId, expectedVersion }: { taskId: number; expectedVersion: number }) => completeDevTask({ organizationId, taskId, expectedVersion }),
    onSuccess: refresh,
  });
  if (summary.isPending) return <Card title="开发交付">正在加载开发交付状态…</Card>;
  if (!summary.data) return <Alert type="error" content="开发交付状态不可用。" />;
  const error = start.error ?? complete.error;
  return (
    <Card title="开发交付">
      {error && <Alert type="error" content={formatRequestError(error)} />}
      <DevelopmentSummaryView summary={summary.data} busy={start.isPending || complete.isPending} onStart={(taskId) => start.mutate(taskId)} onComplete={(taskId, expectedVersion) => complete.mutate({ taskId, expectedVersion })} />
    </Card>
  );
}

export function DevelopmentSummaryView({ summary, busy, onStart, onComplete }: { summary: DevelopmentQaSummary; busy: boolean; onStart: (taskId: number) => void; onComplete: (taskId: number, expectedVersion: number) => void }) {
  return (
    <Space direction="vertical" style={{ width: "100%" }}>
      <Typography.Text>CI 策略：{summary.ciRequired ? "MR 当前 head 必须成功" : "非必需"}</Typography.Text>
      {summary.ciRequired && !summary.repositoryConfigured && <Alert type="warning" content="项目尚未绑定可用 GitLab 仓库，当前策略不会放行 QA。" />}
      {summary.tasks.map((task) => {
        const stale = task.pipelineId != null && task.mergeRequestHeadSha !== task.pipelineCommitSha;
        return (
          <Card key={task.id} size="small" title={`${task.itemKey} · ${task.title}`}>
            <Space direction="vertical">
              <Space>
                <Tag>{task.status}</Tag>
                {task.branchName && <Tag>{task.branchName}</Tag>}
                {task.mergeRequestUrl && (
                  <a href={task.mergeRequestUrl} target="_blank" rel="noreferrer">
                    MR
                  </a>
                )}
                {task.pipelineStatus && <Tag>{task.pipelineStatus}</Tag>}
              </Space>
              {stale && <Alert type="warning" content="Pipeline 不属于 MR 当前 head" />}
              {task.pipelineLastSyncedAt && <Typography.Text type="secondary">快照同步于 {new Date(task.pipelineLastSyncedAt).toLocaleString()}</Typography.Text>}
              {task.status === "TODO" && (
                <Button loading={busy} onClick={() => onStart(task.id)}>
                  启动开发
                </Button>
              )}
              {task.status === "IN_PROGRESS" && (
                <Button loading={busy} onClick={() => onComplete(task.id, task.version)}>
                  完成任务
                </Button>
              )}
            </Space>
          </Card>
        );
      })}
      {summary.tasks.length === 0 && <Typography.Text type="secondary">尚未创建研发任务。</Typography.Text>}
    </Space>
  );
}
