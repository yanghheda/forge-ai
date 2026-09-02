"use client";

import { Alert, Card, Empty, Spin, Typography } from "@arco-design/web-react";

import { isApiError } from "@/lib/api";

import type { ProjectOverviewSummary } from "../api/project-overview-api";

export interface ProjectOverviewViewProps {
  data?: ProjectOverviewSummary | null;
  error?: unknown;
  loading: boolean;
  project: string;
  workspace: string;
}

export function ProjectOverviewView({
  data,
  error,
  loading,
  project,
  workspace,
}: ProjectOverviewViewProps) {
  if (loading) {
    return <Spin tip="正在连接 ForgeAI 服务…" />;
  }

  if (error) {
    const requestId = isApiError(error) ? error.requestId : undefined;
    return (
      <Alert
        type="error"
        title="无法加载项目概览"
        content={requestId ? `请稍后重试。Request ID: ${requestId}` : "请稍后重试。"}
      />
    );
  }

  if (!data) {
    return <Empty description="当前还没有可展示的项目数据" />;
  }

  return (
    <section aria-labelledby="project-overview-title">
      <Typography.Title id="project-overview-title" heading={2}>
        项目概览
      </Typography.Title>
      <Typography.Paragraph>
        Workspace：{workspace} · Project：{project}
      </Typography.Paragraph>
      <Card title="服务连接" bordered>
        <Typography.Text>
          {data.serviceName}：{data.serviceStatus}
        </Typography.Text>
      </Card>
    </section>
  );
}
