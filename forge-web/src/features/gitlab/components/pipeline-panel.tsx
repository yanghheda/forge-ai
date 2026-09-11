"use client";

import { Alert, Button, Card, Input, Message, Space, Spin, Tag, Typography } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import ui from "@/components/workbench/workbench.module.css";
import { formatRequestError } from "@/lib/api";

import { listPipelines, triggerPipeline } from "../api/gitlab-api";

export function PipelinePanel({ organizationId }: { organizationId: number }) {
  const queryClient = useQueryClient();
  const [ref, setRef] = useState("main");
  const pipelines = useQuery({
    queryKey: ["pipelines", organizationId],
    queryFn: () => listPipelines(organizationId),
    refetchInterval: 3_000,
  });
  const trigger = useMutation({
    mutationFn: () => triggerPipeline({ organizationId, ref }),
    onSuccess: () => {
      Message.success("Pipeline 已触发。");
      return queryClient.invalidateQueries({
        queryKey: ["pipelines", organizationId],
      });
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });

  return (
    <Card title="持续集成 · Pipelines">
      <Space direction="vertical" style={{ width: "100%" }}>
        <div className={ui.inlineForm}>
          <Input aria-label="Pipeline 分支或引用" value={ref} onChange={setRef} />
          <Button type="primary" disabled={!ref.trim()} loading={trigger.isPending} onClick={() => trigger.mutate()}>
            触发 Pipeline
          </Button>
        </div>
        {pipelines.isPending && <Spin tip="正在同步 Pipeline 状态…" />}
        {pipelines.isError && <Alert type="error" content={formatRequestError(pipelines.error)} />}
        {pipelines.data?.map((pipeline) => (
          <div className={ui.listItem} key={pipeline.id}>
            <div className={ui.itemMain}>
              <span className={ui.itemTitle}>Pipeline #{pipeline.remotePipelineId}</span>
              <span className={ui.itemMeta}>同步于 {new Date(pipeline.lastSyncedAt).toLocaleString()}</span>
            </div>
            <div className={ui.itemActions}>
              <Tag>{pipeline.ref}</Tag>
              <Tag color={statusColor(pipeline.status)}>{pipeline.status}</Tag>
            </div>
          </div>
        ))}
        {pipelines.data?.length === 0 && <Typography.Text type="secondary">尚无 Pipeline 快照。</Typography.Text>}
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
