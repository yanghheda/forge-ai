"use client";

import { Alert, Button, Card, Input, Space, Spin, Tag, Typography } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { listPipelines, triggerPipeline } from "../api/gitlab-api";

export function PipelinePanel({
  workspaceId,
  projectId,
}: {
  workspaceId: number;
  projectId: number;
}) {
  const queryClient = useQueryClient();
  const [ref, setRef] = useState("main");
  const pipelines = useQuery({
    queryKey: ["pipelines", workspaceId, projectId],
    queryFn: () => listPipelines(workspaceId, projectId),
    refetchInterval: 3_000,
  });
  const trigger = useMutation({
    mutationFn: () => triggerPipeline({ workspaceId, projectId, ref }),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: ["pipelines", workspaceId, projectId],
      }),
  });

  return (
    <Card title="Development · Pipelines">
      <Space direction="vertical" style={{ width: "100%" }}>
        <Space>
          <Input aria-label="Pipeline ref" value={ref} onChange={setRef} />
          <Button
            type="primary"
            disabled={!ref.trim()}
            loading={trigger.isPending}
            onClick={() => trigger.mutate()}
          >
            触发 Pipeline
          </Button>
        </Space>
        {trigger.isError && <Alert type="error" content={trigger.error.message} />}
        {pipelines.isPending && <Spin tip="正在同步 Pipeline 状态…" />}
        {pipelines.isError && <Alert type="error" content={pipelines.error.message} />}
        {pipelines.data?.map((pipeline) => (
          <Card key={pipeline.id} size="small">
            <Space>
              <Typography.Text>#{pipeline.remotePipelineId}</Typography.Text>
              <Tag>{pipeline.ref}</Tag>
              <Tag color={statusColor(pipeline.status)}>{pipeline.status}</Tag>
              <Typography.Text type="secondary">
                同步于 {new Date(pipeline.lastSyncedAt).toLocaleString()}
              </Typography.Text>
            </Space>
          </Card>
        ))}
        {pipelines.data?.length === 0 && (
          <Typography.Text type="secondary">尚无 Pipeline 快照。</Typography.Text>
        )}
      </Space>
    </Card>
  );
}

function statusColor(status: string): string {
  if (status === "success") return "green";
  if (status === "failed") return "red";
  if (status === "canceled" || status === "cancelled") return "gray";
  return "blue";
}
