"use client";

import { Button, Card, Input, Message, Spin, Tag } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { addProjectMember, listProjectMembers, removeProjectMember } from "../api/project-api";
import { RequestError } from "./workspace-projects";
import ui from "@/components/workbench/workbench.module.css";
import { formatRequestError } from "@/lib/api";

export function ProjectMembers({ workspaceId, projectId }: { workspaceId: number; projectId: number }) {
  const queryClient = useQueryClient();
  const [email, setEmail] = useState("");
  const members = useQuery({ queryKey: ["project-members", workspaceId, projectId], queryFn: () => listProjectMembers(workspaceId, projectId) });
  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ["project-members", workspaceId, projectId] });
  const add = useMutation({ mutationFn: () => addProjectMember(workspaceId, projectId, email), onSuccess: () => { Message.success("项目成员已添加。"); setEmail(""); invalidate(); }, onError: (error) => Message.error(formatRequestError(error)) });
  const remove = useMutation({ mutationFn: (userId: number) => removeProjectMember(workspaceId, projectId, userId), onSuccess: () => { Message.success("项目成员已移除。"); invalidate(); }, onError: (error) => Message.error(formatRequestError(error)) });

  if (members.isPending) return <Spin tip="正在加载项目成员…" />;
  if (members.isError) return <RequestError error={members.error} />;
  return <Card title="项目成员" size="small">
    <div className={ui.inlineForm}><Input aria-label="项目成员邮箱" value={email} placeholder="已有账号邮箱" onChange={setEmail} />
    <Button type="primary" disabled={!email.trim()} loading={add.isPending} onClick={() => add.mutate()}>添加成员</Button></div>
    <div className={ui.list}>{members.data.map((member) => <div className={ui.listItem} key={member.id}><span className={ui.itemTitle}>{member.displayName}</span><span className={ui.itemActions}><Tag color={member.active ? "green" : "gray"}>{member.active ? "在职" : "已移除"}</Tag>
      {member.active && <Button type="text" status="danger" loading={remove.isPending} onClick={() => remove.mutate(member.userId)}>移除</Button>}</span>
    </div>)}</div>
  </Card>;
}
