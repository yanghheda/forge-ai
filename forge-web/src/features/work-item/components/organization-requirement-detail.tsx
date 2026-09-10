"use client";

import { Alert, Button, Card, Message, Select, Spin, Tag, Typography } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";

import { formatRequestError } from "@/lib/api";
import { priorityLabel, roleLabel } from "@/lib/labels";
import { getOrganizationRequirement, getRequirementParticipants, listRequirementMembers, replaceRequirementParticipants, type RequirementRole } from "../api/work-item-api";
import styles from "./organization-requirement-detail.module.css";

const roles: RequirementRole[] = ["PRODUCT", "UX", "DEVELOPER", "QA"];

export function OrganizationRequirementDetail({ requirementId }: { requirementId: number }) {
  const queryClient = useQueryClient();
  const [overrides, setOverrides] = useState<Partial<Record<RequirementRole, number | undefined>>>({});
  const requirement = useQuery({ queryKey: ["requirements", requirementId], queryFn: () => getOrganizationRequirement(requirementId) });
  const participants = useQuery({ queryKey: ["requirements", requirementId, "participants"], queryFn: () => getRequirementParticipants(requirementId) });
  const members = useQuery({ queryKey: ["requirements", "members"], queryFn: () => listRequirementMembers() });

  const persisted = Object.fromEntries((participants.data ?? []).map((item) => [item.role, item.userId])) as Partial<Record<RequirementRole, number>>;
  const assignments = { ...persisted, ...overrides };

  const save = useMutation({
    mutationFn: () => replaceRequirementParticipants(requirementId, roles.flatMap((role) => assignments[role] ? [{ role, userId: assignments[role]! }] : [])),
    onSuccess: () => { Message.success("需求协作人员已更新。"); void queryClient.invalidateQueries({ queryKey: ["requirements", requirementId, "participants"] }); },
    onError: (error) => Message.error(formatRequestError(error)),
  });

  if (requirement.isPending) return <Spin tip="正在加载需求…" />;
  if (requirement.isError) return <Alert type="error" content={formatRequestError(requirement.error)} />;
  const item = requirement.data;

  return <section className={styles.page}>
    <Link href="/overview" className={styles.back}>← 返回需求概览</Link>
    <header className={styles.header}><div><span>{item.itemKey}</span><Typography.Title heading={2}>{item.title}</Typography.Title><Typography.Paragraph>{item.description || "尚未补充需求描述。"}</Typography.Paragraph></div><div><Tag color="arcoblue">{item.status}</Tag><Tag>{priorityLabel(item.priority)}优先级</Tag></div></header>
    <div className={styles.grid}>
      <Card title="需求协作人员" className={styles.people}><p>按需求关联产品、UX、开发和测试人员。首个 Owner 无需额外关联即可执行全部流程。</p><div className={styles.roleGrid}>{roles.map((role) => <label key={role}><span>{roleLabel(role)}</span><Select aria-label={`${roleLabel(role)}人员`} allowClear value={assignments[role]} placeholder={`选择${roleLabel(role)}人员`} onChange={(userId) => setOverrides((value) => ({ ...value, [role]: userId }))} options={(members.data ?? []).filter((member) => member.roles.includes(role) || member.roles.includes("OWNER")).map((member) => ({ value: member.userId, label: `${member.displayName} · ${member.email}` }))} /></label>)}</div><Button type="primary" loading={save.isPending} onClick={() => save.mutate()}>保存人员关联</Button></Card>
      <Card title="流程信息"><dl className={styles.facts}><div><dt>当前状态</dt><dd>{item.status}</dd></div><div><dt>优先级</dt><dd>{priorityLabel(item.priority)}</dd></div><div><dt>最近更新</dt><dd>{new Date(item.updatedAt).toLocaleString("zh-CN")}</dd></div><div><dt>数据版本</dt><dd>v{item.version}</dd></div></dl></Card>
    </div>
  </section>;
}
