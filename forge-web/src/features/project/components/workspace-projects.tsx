"use client";

import { Alert, Button, Empty, Input, Spin, Tag } from "@arco-design/web-react";
import { IconArrowRight, IconPlus, IconSearch } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useMemo, useState, type FormEvent } from "react";

import { getCurrentUser } from "@/features/auth";
import { formatRequestError } from "@/lib/api";
import { roleLabel } from "@/lib/labels";

import { createProject, listProjects } from "../api/project-api";
import styles from "./workspace-projects.module.css";

export function WorkspaceProjects({ workspaceSlug }: { workspaceSlug: string }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({ key: "", name: "", description: "" });
  const [query, setQuery] = useState("");
  const [creating, setCreating] = useState(false);
  const currentUser = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser(), retry: false });
  const workspace = currentUser.data?.workspaces.find((item) => item.slug === workspaceSlug);
  const canManageProjects = workspace?.roles.some((role) => role === "OWNER" || role === "ADMIN") ?? false;
  const projects = useQuery({ queryKey: ["projects", workspace?.id], queryFn: () => listProjects(workspace!.id), enabled: workspace !== undefined });
  const create = useMutation({ mutationFn: () => createProject({ workspaceId: workspace!.id, ...form }), onSuccess: () => { setForm({ key: "", name: "", description: "" }); setCreating(false); void queryClient.invalidateQueries({ queryKey: ["projects", workspace?.id] }); } });
  const filtered = useMemo(() => (projects.data ?? []).filter((project) => `${project.key} ${project.name} ${project.description}`.toLowerCase().includes(query.toLowerCase())), [projects.data, query]);

  function submit(event: FormEvent) { event.preventDefault(); if (form.key.trim() && form.name.trim()) create.mutate(); }
  if (currentUser.isPending || projects.isPending) return <div className={styles.loading}><Spin tip="正在加载项目…" /></div>;
  if (!workspace) return <Alert type="error" content="当前账户无权访问此工作空间。" />;
  if (currentUser.isError || projects.isError) return <RequestError error={currentUser.error ?? projects.error} />;

  const active = projects.data.filter((project) => project.status === "ACTIVE").length;
  return <section aria-labelledby="workspace-projects-title">
    <div className={styles.heading}><div><span className={styles.eyebrow}>工作空间</span><h1 id="workspace-projects-title">{workspace.name}</h1><p>集中管理产品交付、团队协作与智能 Agent。</p></div>{canManageProjects && <Button type="primary" icon={<IconPlus />} onClick={() => setCreating((value) => !value)}>{creating ? "取消创建" : "新建项目"}</Button>}</div>
    <div className={styles.stats}><div><small>全部项目</small><strong>{projects.data.length}</strong></div><div><small>活跃项目</small><strong>{active}</strong></div><div><small>我的角色</small><strong className={styles.role}>{workspace.roles.map(roleLabel).join(" · ")}</strong></div></div>
    {creating && <form onSubmit={submit} className={styles.createForm}><div className={styles.formTitle}><div><h2>创建新项目</h2><p>项目 Key 创建后将用于工作项编号与路由。</p></div></div><div className={styles.fields}><label>项目 Key<Input aria-label="项目 Key" placeholder="例如 FORGE" value={form.key} onChange={(key) => setForm((value) => ({ ...value, key: key.toUpperCase() }))} maxLength={12} /></label><label>项目名称<Input aria-label="项目名称" placeholder="面向团队的项目名称" value={form.name} onChange={(name) => setForm((value) => ({ ...value, name }))} /></label><label className={styles.description}>项目说明<Input aria-label="项目说明" placeholder="简要说明目标和范围（可选）" value={form.description} onChange={(description) => setForm((value) => ({ ...value, description }))} /></label></div><div className={styles.formActions}>{create.isError && <RequestError error={create.error} />}<Button onClick={() => setCreating(false)}>取消</Button><Button htmlType="submit" type="primary" disabled={!form.key.trim() || !form.name.trim()} loading={create.isPending}>创建项目</Button></div></form>}
    <div className={styles.toolbar}><div><h2>项目</h2><span>{filtered.length} 个结果</span></div><Input prefix={<IconSearch />} allowClear placeholder="搜索项目…" value={query} onChange={setQuery} /></div>
    {filtered.length === 0 ? <div className={styles.empty}><Empty description={query ? "没有匹配的项目" : "尚未创建项目"} />{!query && canManageProjects && <Button type="primary" onClick={() => setCreating(true)}>创建第一个项目</Button>}</div> : <div className={styles.grid}>{filtered.map((project) => <Link className={styles.projectCard} key={project.id} href={`/w/${workspaceSlug}/p/${project.key}/overview`}><div className={styles.projectTop}><span className={styles.projectIcon}>{project.key.slice(0,2)}</span><Tag color={project.status === "ACTIVE" ? "green" : "gray"}>{project.status === "ACTIVE" ? "活跃" : "已归档"}</Tag></div><h3>{project.name}</h3><b>{project.key}</b><p>{project.description || "尚未填写项目说明。"}</p><footer><span>更新于 {new Date(project.updatedAt).toLocaleDateString("zh-CN")}</span><IconArrowRight /></footer></Link>)}</div>}
  </section>;
}

export function RequestError({ error }: { error: unknown }) { return <Alert type="error" content={formatRequestError(error)} />; }
