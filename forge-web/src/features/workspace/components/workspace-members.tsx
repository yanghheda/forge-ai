"use client";

import { Alert, Button, Card, Input, Select, Spin, Tag } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import ui from "@/components/workbench/workbench.module.css";
import { getCurrentUser } from "@/features/auth";
import { formatRequestError } from "@/lib/api";
import { roleLabel } from "@/lib/labels";

import { addWorkspaceMember, createWorkspaceMember, listWorkspaceMembers, removeWorkspaceMember } from "../api/workspace-api";

const ROLE_OPTIONS = ["OWNER", "ADMIN", "PRODUCT", "UX", "DEVELOPER", "QA", "RELEASE_APPROVER"].map((value) => ({ label: roleLabel(value), value }));

function ErrorAlert({ error }: { error: unknown }) {
  return <Alert type="error" content={formatRequestError(error)} />;
}

export function WorkspaceMembers({ workspaceSlug }: { workspaceSlug: string }) {
  const queryClient = useQueryClient();
  const [email, setEmail] = useState("");
  const [role, setRole] = useState("DEVELOPER");
  const [create, setCreate] = useState({ email: "", displayName: "", password: "", role: "DEVELOPER" });
  const currentUser = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser(), retry: false });
  const workspace = currentUser.data?.workspaces.find((item) => item.slug === workspaceSlug);
  const members = useQuery({ queryKey: ["workspace-members", workspace?.id], queryFn: () => listWorkspaceMembers(workspace!.id), enabled: workspace !== undefined });
  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ["workspace-members", workspace?.id] });
  const add = useMutation({ mutationFn: () => addWorkspaceMember(workspace!.id, email, role), onSuccess: () => { setEmail(""); invalidate(); } });
  const createMutation = useMutation({ mutationFn: () => createWorkspaceMember(workspace!.id, create), onSuccess: () => { setCreate({ email: "", displayName: "", password: "", role: "DEVELOPER" }); invalidate(); } });
  const remove = useMutation({ mutationFn: (userId: number) => removeWorkspaceMember(workspace!.id, userId), onSuccess: invalidate });

  if (currentUser.isPending || members.isPending) return <Spin tip="正在加载工作空间成员…" />;
  if (!workspace) return <Alert type="error" content="当前账户无权访问此工作空间。" />;
  if (currentUser.isError || members.isError) return <ErrorAlert error={currentUser.error ?? members.error} />;
  return <section className={ui.page} aria-labelledby="workspace-members-title">
    <header className={ui.pageHeader}><div><span className={ui.eyebrow}>访问控制</span><h1 id="workspace-members-title">成员与权限</h1><p>管理工作空间账号、角色和访问状态。</p></div></header>
    <div className={ui.twoColumns}>
        <Card size="small" title="创建成员账号">
          <div className={ui.stack}>
            <Input aria-label="新成员邮箱" value={create.email} placeholder="邮箱" onChange={(value) => setCreate((current) => ({ ...current, email: value }))} />
            <Input aria-label="新成员名称" value={create.displayName} placeholder="显示名称" onChange={(value) => setCreate((current) => ({ ...current, displayName: value }))} />
            <Select aria-label="新成员角色" value={create.role} onChange={(value) => setCreate((current) => ({ ...current, role: value }))} options={ROLE_OPTIONS} />
            <Input.Password aria-label="新成员初始密码" value={create.password} placeholder="初始密码" onChange={(value) => setCreate((current) => ({ ...current, password: value }))} />
            <Button type="primary" disabled={!create.email.trim() || !create.displayName.trim() || !create.password} loading={createMutation.isPending} onClick={() => createMutation.mutate()}>创建账号</Button>
          </div>
          {createMutation.isError && <ErrorAlert error={createMutation.error} />}
        </Card>
        <Card size="small" title="添加已有账号">
          <div className={ui.stack}>
            <Input aria-label="工作空间成员邮箱" value={email} placeholder="已有账号邮箱" onChange={setEmail} />
            <Select aria-label="成员角色" value={role} onChange={setRole} options={ROLE_OPTIONS} />
            <Button type="primary" disabled={!email.trim()} loading={add.isPending} onClick={() => add.mutate()}>添加成员</Button>
          </div>
          {add.isError && <ErrorAlert error={add.error} />}
        </Card>
    </div>
    <Card title={`工作空间成员 · ${members.data.length}`}>
      <div className={ui.list}>{members.data.map((member) => <div className={ui.listItem} key={member.id}><span className={ui.itemMain}><span className={ui.itemTitle}>{member.displayName}</span><span className={ui.itemMeta}>{member.email}</span></span><span className={ui.itemActions}><Tag color={member.active ? "green" : "gray"}>{member.active ? "在职" : "已移除"}</Tag>
          {member.active && <Button type="text" status="danger" loading={remove.isPending} onClick={() => remove.mutate(member.userId)}>移除</Button>}</span>
        </div>)}</div>
    </Card>
  </section>;
}
