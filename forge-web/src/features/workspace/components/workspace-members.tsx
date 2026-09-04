"use client";

import { Alert, Button, Card, Input, Select, Space, Spin } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { getCurrentUser } from "@/features/auth";
import { isApiError } from "@/lib/api";

import { addWorkspaceMember, createWorkspaceMember, listWorkspaceMembers, removeWorkspaceMember } from "../api/workspace-api";

const ROLE_OPTIONS = ["OWNER", "ADMIN", "PRODUCT", "UX", "DEVELOPER", "QA", "RELEASE_APPROVER"].map((value) => ({ label: value, value }));

function ErrorAlert({ error }: { error: unknown }) {
  const requestId = isApiError(error) ? error.requestId : undefined;
  return <Alert type="error" content={requestId ? `请求失败。Request ID: ${requestId}` : "请求失败，请稍后重试。"} />;
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

  if (currentUser.isPending || members.isPending) return <Spin tip="正在加载 Workspace 成员…" />;
  if (!workspace) return <Alert type="error" content="当前账户无权访问此 Workspace。" />;
  if (currentUser.isError || members.isError) return <ErrorAlert error={currentUser.error ?? members.error} />;
  return <section aria-labelledby="workspace-members-title">
    <Card title="Workspace 成员" id="workspace-members-title">
      <Space direction="vertical" style={{ width: "100%" }}>
        <Card size="small" title="创建成员账号">
          <Space>
            <Input aria-label="新成员邮箱" value={create.email} placeholder="邮箱" onChange={(value) => setCreate((current) => ({ ...current, email: value }))} />
            <Input aria-label="新成员名称" value={create.displayName} placeholder="显示名称" onChange={(value) => setCreate((current) => ({ ...current, displayName: value }))} />
            <Select aria-label="新成员角色" value={create.role} onChange={(value) => setCreate((current) => ({ ...current, role: value }))} options={ROLE_OPTIONS} />
          </Space>
          <Space>
            <Input.Password aria-label="新成员初始密码" value={create.password} placeholder="初始密码" onChange={(value) => setCreate((current) => ({ ...current, password: value }))} />
            <Button type="primary" disabled={!create.email.trim() || !create.displayName.trim() || !create.password} loading={createMutation.isPending} onClick={() => createMutation.mutate()}>创建账号</Button>
          </Space>
          {createMutation.isError && <ErrorAlert error={createMutation.error} />}
        </Card>
        <Card size="small" title="添加已有账号">
          <Space>
            <Input aria-label="Workspace 成员邮箱" value={email} placeholder="已有账号邮箱" onChange={setEmail} />
            <Select aria-label="成员角色" value={role} onChange={setRole} options={ROLE_OPTIONS} />
            <Button type="primary" disabled={!email.trim()} loading={add.isPending} onClick={() => add.mutate()}>添加成员</Button>
          </Space>
          {add.isError && <ErrorAlert error={add.error} />}
        </Card>
        <ul>{members.data.map((member) => <li key={member.id}>{member.displayName} · {member.email} · {member.active ? "ACTIVE" : "REMOVED"}
          {member.active && <Button type="text" status="danger" loading={remove.isPending} onClick={() => remove.mutate(member.userId)}>移除</Button>}
        </li>)}</ul>
      </Space>
    </Card>
  </section>;
}