"use client";

import { Alert, Button, Card, Empty, Input, Spin, Typography } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState, type FormEvent } from "react";

import { getCurrentUser } from "@/features/auth";
import { isApiError } from "@/lib/api";

import { createProject, listProjects } from "../api/project-api";

export function WorkspaceProjects({ workspaceSlug }: { workspaceSlug: string }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({ key: "", name: "", description: "" });
  const currentUser = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser(), retry: false });
  const workspace = currentUser.data?.workspaces.find((item) => item.slug === workspaceSlug);
  const canManageProjects = workspace?.roles.some((role) => role === "OWNER" || role === "ADMIN") ?? false;
  const projects = useQuery({
    queryKey: ["projects", workspace?.id],
    queryFn: () => listProjects(workspace!.id),
    enabled: workspace !== undefined,
  });
  const create = useMutation({
    mutationFn: () => createProject({ workspaceId: workspace!.id, ...form }),
    onSuccess: () => {
      setForm({ key: "", name: "", description: "" });
      void queryClient.invalidateQueries({ queryKey: ["projects", workspace?.id] });
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (form.key.trim() && form.name.trim()) create.mutate();
  }

  if (currentUser.isPending || projects.isPending) return <Spin tip="正在加载项目…" />;
  if (!workspace) return <Alert type="error" content="当前账户无权访问此 Workspace。" />;
  if (currentUser.isError || projects.isError) return <RequestError error={currentUser.error ?? projects.error} />;

  return <section aria-labelledby="workspace-projects-title">
    <Typography.Title id="workspace-projects-title" heading={2}>项目</Typography.Title>
    {canManageProjects && <Card title="创建项目" size="small">
      <form onSubmit={submit}>
        <Input aria-label="项目 Key" placeholder="项目 Key，例如 FORGE" value={form.key} onChange={(key) => setForm((value) => ({ ...value, key }))} />
        <Input aria-label="项目名称" placeholder="项目名称" value={form.name} onChange={(name) => setForm((value) => ({ ...value, name }))} />
        <Input aria-label="项目说明" placeholder="可选项目说明" value={form.description} onChange={(description) => setForm((value) => ({ ...value, description }))} />
        <Button htmlType="submit" type="primary" loading={create.isPending}>创建 Project</Button>
        {create.isError && <RequestError error={create.error} />}
      </form>
    </Card>}
    {projects.data.length === 0 ? <Empty description="尚未创建项目" /> : <ul>{projects.data.map((project) => <li key={project.id}>
      <Link href={`/w/${workspaceSlug}/p/${project.key}/overview`}>{project.key} · {project.name}</Link> · {project.status}
    </li>)}</ul>}
  </section>;
}

export function RequestError({ error }: { error: unknown }) {
  const requestId = isApiError(error) ? error.requestId : undefined;
  return <Alert type="error" content={requestId ? `请求失败。Request ID: ${requestId}` : "请求失败，请稍后重试。"} />;
}
