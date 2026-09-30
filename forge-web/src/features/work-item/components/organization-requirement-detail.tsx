import { Alert, Button, Card, Form, Input, Message, Select, Space, Spin, Tag } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router";
import { useState, type Dispatch, type SetStateAction } from "react";

import { formatRequestError } from "@/lib/api";
import { priorityLabel, roleLabel } from "@/lib/labels";
import { getOrganizationRequirement, getRequirementActivity, getRequirementDetails, getRequirementParticipants, listRequirementMembers, replaceRequirementParticipants, updateWorkItem, type RequirementMember, type RequirementRole } from "../api/work-item-api";
import { formatDate, statusLabel } from "../utils/requirement-detail-display";
import { ActivityCard, BasicInfo, DescriptionCard, MembersCard, RequirementSteps } from "./requirement-detail-sections";
import { RequirementWorkflowControls } from "./requirement-workflow-controls";
import { RequirementExecutors } from "./requirement-executors";
import { RequirementMaterials } from "./requirement-materials";
import styles from "./organization-requirement-detail.module.css";

const roles: RequirementRole[] = ["PRODUCT", "UX", "DEVELOPER", "QA"];

export function OrganizationRequirementDetail({ requirementId }: { requirementId: number }) {
  const queryClient = useQueryClient();
  const [managing, setManaging] = useState(false);
  const [editingDescription, setEditingDescription] = useState(false);
  const [descriptionDraft, setDescriptionDraft] = useState("");
  const [overrides, setOverrides] = useState<Partial<Record<RequirementRole, number | undefined>>>({});
  const requirement = useQuery({ queryKey: ["requirements", requirementId], queryFn: () => getOrganizationRequirement(requirementId) });
  const materials = useQuery({ queryKey: ["requirements", requirementId, "details"], queryFn: () => getRequirementDetails(requirementId) });
  const participants = useQuery({ queryKey: ["requirements", requirementId, "participants"], queryFn: () => getRequirementParticipants(requirementId) });
  const members = useQuery({ queryKey: ["requirements", "members"], queryFn: () => listRequirementMembers(), enabled: managing });
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
  return (
    <section className={styles.page}>
      <div className={styles.head}>
        <div>
          <Link to="/requirements" className={styles.back}>
            ← 返回需求列表
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
        </main>
        <aside className={styles.column}>
          <RequirementWorkflowControls requirementId={requirementId} />
          <ActivityCard activity={activity.data ?? []} />
          <RequirementExecutors requirementId={requirementId} status={item.status} />
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
          <Form.Item key={role} label={`${roleLabel(role)}负责人`}>
            <Select
              allowClear
              value={assignments[role]}
              placeholder={`选择${roleLabel(role)}管理员`}
              onChange={(userId) => setOverrides((value) => ({ ...value, [role]: userId }))}
              options={members.filter((member) => member.roles.includes(`${role}_ADMIN`)).map((member) => ({ value: member.userId, label: `${member.displayName} · ${member.email}` }))}
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
