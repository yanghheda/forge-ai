"use client";

import { Alert, Button, Card, Form, Message, Select, Spin, Tag } from "@arco-design/web-react";
import { IconDown, IconPause, IconRight } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState, type Dispatch, type SetStateAction } from "react";

import { formatRequestError } from "@/lib/api";
import { priorityLabel, roleLabel } from "@/lib/labels";
import { getOrganizationRequirement, getRequirementActivity, getRequirementDetails, getRequirementParticipants, getRequirementWorkflow, listRequirementMembers, replaceRequirementParticipants, transitionRequirementWorkflow, type RequirementMember, type RequirementRole } from "../api/work-item-api";
import { actionLabel, formatDate, statusLabel } from "../utils/requirement-detail-display";
import { ActivityCard, BasicInfo, DescriptionCard, MembersCard, RequirementSteps, StageCard } from "./requirement-detail-sections";
import { RequirementMaterials } from "./requirement-materials";
import styles from "./organization-requirement-detail.module.css";

const roles: RequirementRole[] = ["PRODUCT", "UX", "DEVELOPER", "QA"];

export function OrganizationRequirementDetail({ requirementId }: { requirementId: number }) {
  const queryClient = useQueryClient();
  const [managing, setManaging] = useState(false);
  const [overrides, setOverrides] = useState<Partial<Record<RequirementRole, number | undefined>>>({});
  const requirement = useQuery({ queryKey: ["requirements", requirementId], queryFn: () => getOrganizationRequirement(requirementId) });
  const materials = useQuery({ queryKey: ["requirements", requirementId, "details"], queryFn: () => getRequirementDetails(requirementId) });
  const participants = useQuery({ queryKey: ["requirements", requirementId, "participants"], queryFn: () => getRequirementParticipants(requirementId) });
  const members = useQuery({ queryKey: ["requirements", "members"], queryFn: () => listRequirementMembers(), enabled: managing });
  const workflow = useQuery({ queryKey: ["requirements", requirementId, "workflow"], queryFn: () => getRequirementWorkflow(requirementId) });
  const activity = useQuery({ queryKey: ["requirements", requirementId, "activity"], queryFn: () => getRequirementActivity(requirementId) });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["requirements", requirementId] });
  const persisted = Object.fromEntries((participants.data ?? []).map((item) => [item.role, item.userId])) as Partial<Record<RequirementRole, number>>;
  const assignments = { ...persisted, ...overrides };
  const save = useMutation({
    mutationFn: () =>
      replaceRequirementParticipants(
        requirementId,
        roles.flatMap((role) => (assignments[role] ? [{ role, userId: assignments[role]! }] : [])),
      ),
    onSuccess: () => {
      Message.success("需求协作人员已更新。");
      setManaging(false);
      setOverrides({});
      void invalidate();
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const transition = useMutation({
    mutationFn: () => transitionRequirementWorkflow(requirementId, workflow.data!.availableActions[0], workflow.data!.version),
    onSuccess: () => void invalidate(),
    onError: (error) => Message.error(formatRequestError(error)),
  });

  if (requirement.isPending || materials.isPending || participants.isPending) return <Spin tip="正在加载需求…" />;
  if (requirement.isError || materials.isError || !requirement.data || !materials.data) return <Alert type="error" content={formatRequestError(requirement.error ?? materials.error)} />;
  const item = requirement.data;
  const nextAction = workflow.data?.availableActions[0];
  return (
    <section className={styles.page}>
      <div className={styles.head}>
        <div>
          <Link href="/overview" className={styles.back}>
            ← 返回列表
          </Link>
          <div className={styles.titleLine}>
            <code>{item.itemKey}</code>
            <h1>{item.title}</h1>
            <Tag color="arcoblue">● {statusLabel[item.status] ?? item.status}</Tag>
            <Tag color="red">⚑ {priorityLabel(item.priority)}优先级</Tag>
          </div>
          <p>
            创建人 {item.reporterName} · 创建于 {formatDate(item.createdAt, true)} · 期望发布 {formatDate(item.dueAt)}
          </p>
        </div>
        <div className={styles.actions}>
          <Button type="text">
            更多 <IconDown />
          </Button>
          <Button icon={<IconPause />}>暂停 Agent</Button>
          <Button type="primary" icon={<IconRight />} disabled={!nextAction} loading={transition.isPending} onClick={() => transition.mutate()}>
            {nextAction ? (actionLabel[nextAction] ?? "推进到下一阶段") : "推进到下一阶段"}
          </Button>
        </div>
      </div>
      <RequirementSteps item={item} />
      <div className={styles.grid}>
        <main className={styles.column}>
          <BasicInfo item={item} participants={participants.data ?? []} />
          <DescriptionCard item={item} />
          <RequirementMaterials key={materials.data.version} workItemId={requirementId} details={materials.data} onChanged={invalidate} />
          <ActivityCard activity={activity.data ?? []} />
        </main>
        <aside className={styles.column}>
          <StageCard item={item} workflow={workflow.data} />
          <MembersCard item={item} participants={participants.data ?? []} onManage={() => setManaging((value) => !value)} />
          {managing && <MemberEditor assignments={assignments} members={members.data ?? []} setOverrides={setOverrides} saving={save.isPending} onSave={() => save.mutate()} />}
        </aside>
      </div>
    </section>
  );
}

function MemberEditor({
  assignments,
  members,
  setOverrides,
  saving,
  onSave,
}: {
  assignments: Partial<Record<RequirementRole, number | undefined>>;
  members: RequirementMember[];
  setOverrides: Dispatch<SetStateAction<Partial<Record<RequirementRole, number | undefined>>>>;
  saving: boolean;
  onSave: () => void;
}) {
  return (
    <Card title="管理协作成员">
      <Form layout="vertical" onSubmit={onSave}>
        {roles.map((role) => (
          <Form.Item key={role} label={roleLabel(role)}>
            <Select
              allowClear
              value={assignments[role]}
              placeholder={`选择${roleLabel(role)}人员`}
              onChange={(userId) => setOverrides((value) => ({ ...value, [role]: userId }))}
              options={members.filter((member) => member.roles.includes(role) || member.roles.includes("OWNER")).map((member) => ({ value: member.userId, label: `${member.displayName} · ${member.email}` }))}
            />
          </Form.Item>
        ))}
        <Button htmlType="submit" type="primary" long loading={saving}>
          保存人员关联
        </Button>
      </Form>
    </Card>
  );
}
