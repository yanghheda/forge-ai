"use client";

import { Alert, Button, Card, Input, Spin } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { getCurrentUser } from "@/features/auth";
import { isApiError } from "@/lib/api";

import { addWorkspaceMember, listWorkspaceMembers, removeWorkspaceMember } from "../api/workspace-api";

function ErrorAlert({ error }: { error: unknown }) {
  const requestId = isApiError(error) ? error.requestId : undefined;
  return <Alert type="error" content={requestId ? `请求失败。Request ID: ${requestId}` : "请求失败，请稍后重试。"} />;
}

export function WorkspaceMembers({ workspaceSlug }: { workspaceSlug: string }) {
  const queryClient = useQueryClient();
  const [email, setEmail] = useState("");
  const currentUser = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser(), retry: false });
  const workspace = currentUser.data?.workspaces.find((item) => item.slug === workspaceSlug);
  const members = useQuery({ queryKey: ["workspace-members", workspace?.id], queryFn: () => listWorkspaceMembers(workspace!.id), enabled: workspace !== undefined });
  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ["workspace-members", workspace?.id] });
  const add = useMutation({ mutationFn: () => addWorkspaceMember(workspace!.id, email), onSuccess: () => { setEmail(""); invalidate(); } });
  const remove = useMutation({ mutationFn: (userId: number) => removeWorkspaceMember(workspace!.id, userId), onSuccess: invalidate });

  if (currentUser.isPending || members.isPending) return <Spin tip="正在加载 Workspace 成员…" />;
  if (!workspace) return <Alert type="error" content="当前账户无权访问此 Workspace。" />;
  if (currentUser.isError || members.isError) return <ErrorAlert error={currentUser.error ?? members.error} />;
  return <section aria-labelledby="workspace-members-title">
    <Card title="Workspace 成员" id="workspace-members-title">
      <p>仅可添加已经存在的本地账号；账号邀请与创建留待后续身份能力。</p>
      <Input aria-label="Workspace 成员邮箱" value={email} placeholder="已有账号邮箱" onChange={setEmail} />
      <Button type="primary" disabled={!email.trim()} loading={add.isPending} onClick={() => add.mutate()}>添加成员</Button>
      {add.isError && <ErrorAlert error={add.error} />}
      <ul>{members.data.map((member) => <li key={member.id}>{member.displayName} · {member.email} · {member.active ? "ACTIVE" : "REMOVED"}
        {member.active && <Button type="text" status="danger" loading={remove.isPending} onClick={() => remove.mutate(member.userId)}>移除</Button>}
      </li>)}</ul>
    </Card>
  </section>;
}
