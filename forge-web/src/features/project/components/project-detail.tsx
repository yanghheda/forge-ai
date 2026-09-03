"use client";

import { Alert, Button, Card, Spin, Typography } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { getCurrentUser } from "@/features/auth";

import { archiveProject, getProject, listProjects } from "../api/project-api";
import { ProjectMembers } from "./project-members";
import { RequestError } from "./workspace-projects";

export function ProjectDetail({ workspaceSlug, projectKey }: { workspaceSlug: string; projectKey: string }) {
  const queryClient = useQueryClient();
  const currentUser = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser(), retry: false });
  const workspace = currentUser.data?.workspaces.find((item) => item.slug === workspaceSlug);
  const projects = useQuery({ queryKey: ["projects", workspace?.id], queryFn: () => listProjects(workspace!.id), enabled: workspace !== undefined });
  const projectRef = projects.data?.find((item) => item.key === projectKey);
  const project = useQuery({ queryKey: ["project", workspace?.id, projectRef?.id], queryFn: () => getProject(workspace!.id, projectRef!.id), enabled: projectRef !== undefined });
  const archive = useMutation({
    mutationFn: () => archiveProject({ workspaceId: workspace!.id, projectId: project.data!.id, expectedVersion: project.data!.version }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["projects", workspace?.id] }),
  });

  if (currentUser.isPending || projects.isPending || project.isPending) return <Spin tip="正在加载项目…" />;
  if (!workspace || !projectRef) return <Alert type="error" content="项目不存在或当前账户无权访问。" />;
  if (currentUser.isError || projects.isError || project.isError) return <RequestError error={currentUser.error ?? projects.error ?? project.error} />;
  if (!project.data) return <Alert type="error" content="项目不存在或当前账户无权访问。" />;

  return <section aria-labelledby="project-detail-title">
    <Typography.Title id="project-detail-title" heading={2}>{project.data.key} · {project.data.name}</Typography.Title>
    <Typography.Paragraph>{project.data.description || "尚未填写项目说明。"}</Typography.Paragraph>
    <Card title="项目状态" size="small">
      <Typography.Text>{project.data.status} · version {project.data.version}</Typography.Text>
      {project.data.status === "ACTIVE" && <Button status="warning" loading={archive.isPending} onClick={() => archive.mutate()}>归档 Project</Button>}
      {archive.isError && <RequestError error={archive.error} />}
    </Card>
    <ProjectMembers workspaceId={workspace.id} projectId={project.data.id} />
  </section>;
}
