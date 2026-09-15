"use client";

import { Alert, Button, Card, Form, Input, Message, Select, Space, Spin, Tag } from "@arco-design/web-react";
import { IconDown, IconPause, IconRight } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState, type Dispatch, type SetStateAction } from "react";

import { formatRequestError } from "@/lib/api";
import { RequirementAgentCard } from "@/features/agent-run";
import { priorityLabel, roleLabel } from "@/lib/labels";
import {
  getOrganizationRequirement,
  getRequirementActivity,
  getRequirementDetails,
  getRequirementParticipants,
  getRequirementWorkflow,
  listRequirementMembers,
  replaceRequirementParticipants,
  transitionRequirementWorkflow,
  updateWorkItem,
  type RequirementMember,
  type RequirementRole,
  type RequirementWorkflowAction,
} from "../api/work-item-api";
import { actionLabel, formatDate, statusLabel } from "../utils/requirement-detail-display";
import { ActivityCard, BasicInfo, DescriptionCard, MembersCard, RequirementSteps, StageCard } from "./requirement-detail-sections";
import { RequirementMaterials } from "./requirement-materials";
import styles from "./organization-requirement-detail.module.css";

const roles: RequirementRole[] = ["PRODUCT", "UX", "DEVELOPER", "QA"];

export function OrganizationRequirementDetail({ requirementId }: { requirementId: number }) {
  const queryClient = useQueryClient();
  const [managing, setManaging] = useState(false);
  const [reviewReason, setReviewReason] = useState("");
  const [editingDescription, setEditingDescription] = useState(false);
  const [descriptionDraft, setDescriptionDraft] = useState("");
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
    mutationFn: ({ action, reason }: { action: RequirementWorkflowAction; reason?: string }) => transitionRequirementWorkflow(requirementId, action, workflow.data!.version, reason ? { reason } : {}),
    onSuccess: () => {
      setReviewReason("");
      void invalidate();
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const updateDescription = useMutation({
    mutationFn: (input: { description: string; expectedVersion: number }) => updateWorkItem(requirementId, input),
    onSuccess: () => {
      Message.success("需求描述已更新。");
      setEditingDescription(false);
      void invalidate();
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });

  if (requirement.isPending || materials.isPending || participants.isPending) return <Spin tip="正在加载需求…" />;
  if (requirement.isError || materials.isError || !requirement.data || !materials.data) return <Alert type="error" content={formatRequestError(requirement.error ?? materials.error)} />;
  const item = requirement.data;
  const nextAction = item.status === "PRODUCT_REVIEW" ? undefined : workflow.data?.availableActions[0];
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
          <Button type="primary" icon={<IconRight />} disabled={!nextAction} loading={transition.isPending} onClick={() => nextAction && transition.mutate({ action: nextAction })}>
            {nextAction ? (actionLabel[nextAction] ?? "推进到下一阶段") : "推进到下一阶段"}
          </Button>
        </div>
      </div>
      <RequirementSteps item={item} />
      <div className={styles.grid}>
        <main className={styles.column}>
          <BasicInfo item={item} participants={participants.data ?? []} />
          {editingDescription ? (
            <Card title="编辑需求描述">
              <Input.TextArea aria-label="需求描述正文" value={descriptionDraft} onChange={setDescriptionDraft} placeholder="补充业务背景、目标、用户价值和范围边界" maxLength={10000} showWordLimit autoSize={{ minRows: 5, maxRows: 12 }} />
              <Space style={{ marginTop: 12 }}>
                <Button disabled={updateDescription.isPending} onClick={() => setEditingDescription(false)}>
                  取消
                </Button>
                <Button type="primary" loading={updateDescription.isPending} disabled={!descriptionDraft.trim()} onClick={() => updateDescription.mutate({ description: descriptionDraft.trim(), expectedVersion: item.version })}>
                  保存描述
                </Button>
              </Space>
              {updateDescription.isError && <Alert style={{ marginTop: 12 }} type="error" content={formatRequestError(updateDescription.error)} />}
            </Card>
          ) : (
            <DescriptionCard
              item={item}
              extra={
                <Button
                  onClick={() => {
                    setDescriptionDraft(item.description);
                    setEditingDescription(true);
                  }}
                >
                  编辑描述
                </Button>
              }
            />
          )}
          <RequirementMaterials key={materials.data.version} workItemId={requirementId} details={materials.data} onChanged={invalidate} />
          <ActivityCard activity={activity.data ?? []} />
        </main>
        <aside className={styles.column}>
          <RequirementAgentCard requirementId={requirementId} />
          {item.status === "PRODUCT_REVIEW" && workflow.data && (
            <Card title="产品评审决策">
              <p>请核对需求描述与已发布 PRD，再决定通过或退回修改。</p>
              {workflow.data.availableActions.includes("REJECT_PRODUCT_REVIEW") && <Input.TextArea aria-label="退回原因" value={reviewReason} onChange={setReviewReason} placeholder="退回时必须填写具体修改意见" />}
              <Space style={{ marginTop: 12 }}>
                {workflow.data.availableActions.includes("REJECT_PRODUCT_REVIEW") && (
                  <Button status="danger" disabled={!reviewReason.trim()} loading={transition.isPending} onClick={() => transition.mutate({ action: "REJECT_PRODUCT_REVIEW", reason: reviewReason.trim() })}>
                    退回修改
                  </Button>
                )}
                {workflow.data.availableActions.includes("APPROVE_PRODUCT_REVIEW") && (
                  <Button type="primary" loading={transition.isPending} onClick={() => transition.mutate({ action: "APPROVE_PRODUCT_REVIEW" })}>
                    通过产品评审
                  </Button>
                )}
              </Space>
              {!workflow.data.availableActions.some((action) => action === "APPROVE_PRODUCT_REVIEW" || action === "REJECT_PRODUCT_REVIEW") && <Alert style={{ marginTop: 12 }} type="warning" content="当前账号没有产品评审权限，请由 Product Reviewer 或 Owner 操作。" />}
            </Card>
          )}
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
