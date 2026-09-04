"use client";

import { Alert, Card, Spin, Typography } from "@arco-design/web-react";
import { useQuery } from "@tanstack/react-query";
import { getCurrentUser } from "@/features/auth";
import { listProjects } from "@/features/project";
import { listUxTasks } from "../api/work-item-api";

export function UxWorkspace({
  workspaceSlug,
  projectKey,
}: {
  workspaceSlug: string;
  projectKey: string;
}) {
  const user = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
  const workspace = user.data?.workspaces.find((item) => item.slug === workspaceSlug);
  const projects = useQuery({
    queryKey: ["projects", workspace?.id],
    queryFn: () => listProjects(workspace!.id),
    enabled: !!workspace,
  });
  const project = projects.data?.find((item) => item.key === projectKey);
  const tasks = useQuery({
    queryKey: ["work-items", project?.id, "UX_TASK"],
    queryFn: () => listUxTasks(workspace!.id, project!.id),
    enabled: !!workspace && !!project,
  });
  if (user.isPending || projects.isPending || tasks.isPending) return <Spin tip="加载 UX 队列…" />;
  if (!workspace || !project) return <Alert type="error" content="项目不存在或无 UX 读取权限。" />;
  return (
    <Card title="UX Workspace">
      {tasks.data?.items.length === 0 ? (
        <Typography.Text>暂无待处理 UX Task。</Typography.Text>
      ) : (
        tasks.data?.items.map((task) => (
          <p key={task.id}>{task.itemKey} · {task.title} · {task.status}</p>
        ))
      )}
    </Card>
  );
}
