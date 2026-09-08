"use client";

import { Alert, Button, Spin, Typography } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { getCurrentUser } from "@/features/auth";
import { PipelinePanel } from "@/features/gitlab";
import { ReleasePanel } from "@/features/release";
import { ProductSlice } from "@/features/work-item";

import { archiveProject, getProject, listProjects } from "../api/project-api";
import { ProjectMembers } from "./project-members";
import { RequestError } from "./workspace-projects";
import styles from "./project-detail.module.css";

export function ProjectDetail({
  workspaceSlug,
  projectKey,
}: {
  workspaceSlug: string;
  projectKey: string;
}) {
  const queryClient = useQueryClient();
  const currentUser = useQuery({
    queryKey: ["current-user"],
    queryFn: () => getCurrentUser(),
    retry: false,
  });
  const workspace = currentUser.data?.workspaces.find(
    (item) => item.slug === workspaceSlug,
  );
  const canManageProjects =
    workspace?.roles.some((role) => role === "OWNER" || role === "ADMIN") ??
    false;
  const projects = useQuery({
    queryKey: ["projects", workspace?.id],
    queryFn: () => listProjects(workspace!.id),
    enabled: workspace !== undefined,
  });
  const projectRef = projects.data?.find((item) => item.key === projectKey);
  const project = useQuery({
    queryKey: ["project", workspace?.id, projectRef?.id],
    queryFn: () => getProject(workspace!.id, projectRef!.id),
    enabled: projectRef !== undefined,
  });
  const archive = useMutation({
    mutationFn: () =>
      archiveProject({
        workspaceId: workspace!.id,
        projectId: project.data!.id,
        expectedVersion: project.data!.version,
      }),
    onSuccess: () =>
      void queryClient.invalidateQueries({
        queryKey: ["projects", workspace?.id],
      }),
  });

  if (currentUser.isPending || projects.isPending || project.isPending)
    return <Spin tip="正在加载项目…" />;
  if (!workspace || !projectRef)
    return <Alert type="error" content="项目不存在或当前账户无权访问。" />;
  if (currentUser.isError || projects.isError || project.isError)
    return (
      <RequestError
        error={currentUser.error ?? projects.error ?? project.error}
      />
    );
  if (!project.data)
    return <Alert type="error" content="项目不存在或当前账户无权访问。" />;

  return (
    <section aria-labelledby="project-detail-title">
      <div className={styles.hero}><div><div className={styles.eyebrow}>{project.data.key} · PROJECT OVERVIEW</div><Typography.Title id="project-detail-title" heading={2}>{project.data.name}</Typography.Title><Typography.Paragraph>{project.data.description || "尚未填写项目说明。"}</Typography.Paragraph></div><div className={styles.heroActions}><span className={styles.status}><i />{project.data.status === "ACTIVE" ? "项目活跃" : "已归档"}</span>
        {canManageProjects && project.data.status === "ACTIVE" && (
          <Button status="warning" loading={archive.isPending} onClick={() => archive.mutate()}>归档 Project</Button>
        )}
      </div></div>
      {archive.isError && <RequestError error={archive.error} />}
      <div className={styles.metrics}><article><small>当前版本</small><strong>v{project.data.version}</strong><span>业务数据版本</span></article><article><small>交付阶段</small><strong>Product</strong><span>等待需求进入流程</span></article><article><small>更新时间</small><strong>{new Date(project.data.updatedAt).toLocaleDateString("zh-CN")}</strong><span>最近项目变更</span></article></div>
      <div className={styles.sectionHeading} id="requirements"><div><span>DELIVERY PIPELINE</span><h2>交付工作台</h2></div><p>按阶段查看进度、风险与下一步操作</p></div>
      <div className={styles.panels}><ProductSlice
        workspaceId={workspace.id}
        projectId={project.data.id}
        workspaceSlug={workspaceSlug}
        projectKey={projectKey}
      /><PipelinePanel workspaceId={workspace.id} projectId={project.data.id} /><ReleasePanel workspaceId={workspace.id} projectId={project.data.id} /></div>
      {canManageProjects && (
        <div className={styles.members}><ProjectMembers workspaceId={workspace.id} projectId={project.data.id} /></div>
      )}
    </section>
  );
}
