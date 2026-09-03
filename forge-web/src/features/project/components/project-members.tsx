"use client";

import { Button, Card, Input, Spin } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { addProjectMember, listProjectMembers, removeProjectMember } from "../api/project-api";
import { RequestError } from "./workspace-projects";

export function ProjectMembers({ workspaceId, projectId }: { workspaceId: number; projectId: number }) {
  const queryClient = useQueryClient();
  const [email, setEmail] = useState("");
  const members = useQuery({ queryKey: ["project-members", workspaceId, projectId], queryFn: () => listProjectMembers(workspaceId, projectId) });
  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ["project-members", workspaceId, projectId] });
  const add = useMutation({ mutationFn: () => addProjectMember(workspaceId, projectId, email), onSuccess: () => { setEmail(""); invalidate(); } });
  const remove = useMutation({ mutationFn: (userId: number) => removeProjectMember(workspaceId, projectId, userId), onSuccess: invalidate });

  if (members.isPending) return <Spin tip="正在加载项目成员…" />;
  if (members.isError) return <RequestError error={members.error} />;
  return <Card title="Project 成员" size="small">
    <Input aria-label="项目成员邮箱" value={email} placeholder="已有账号邮箱" onChange={setEmail} />
    <Button type="primary" disabled={!email.trim()} loading={add.isPending} onClick={() => add.mutate()}>添加成员</Button>
    {add.isError && <RequestError error={add.error} />}
    <ul>{members.data.map((member) => <li key={member.id}>{member.displayName} · {member.active ? "ACTIVE" : "REMOVED"}
      {member.active && <Button type="text" status="danger" loading={remove.isPending} onClick={() => remove.mutate(member.userId)}>移除</Button>}
    </li>)}</ul>
  </Card>;
}
