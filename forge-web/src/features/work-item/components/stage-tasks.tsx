import { Alert, Button, Card, Input, Message, Select, Space, Spin, Tag } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { getCurrentUser } from "@/features/auth";
import { formatRequestError } from "@/lib/api";
import { guardHintLabel, workflowActionLabel } from "@/lib/labels";
import { createStageTask, getOrganizationRequirement, getRequirementParticipants, listRequirementMembers, listRequirementTasks, transitionTask, type TaskType, type WorkItemDetail } from "../api/work-item-api";
import { statusLabel } from "../utils/requirement-detail-display";

const domains = { UX_TASK: { role: "UX", preparation: "READY_FOR_UX", label: "设计任务" }, DEV_TASK: { role: "DEVELOPER", preparation: "READY_FOR_DEV", label: "开发任务" }, QA_TASK: { role: "QA", preparation: "READY_FOR_QA", label: "测试任务" } } as const;
export function StageTasks({ requirementId, type }: { requirementId: number; type: TaskType }) {
  const client = useQueryClient();
  const domain = domains[type];
  const [title, setTitle] = useState("");
  const [assignee, setAssignee] = useState<number>();
  const [reason, setReason] = useState("");
  const user = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
  const requirement = useQuery({ queryKey: ["requirements", requirementId], queryFn: () => getOrganizationRequirement(requirementId) });
  const participants = useQuery({ queryKey: ["requirements", requirementId, "participants"], queryFn: () => getRequirementParticipants(requirementId) });
  const members = useQuery({ queryKey: ["requirements", "members"], queryFn: () => listRequirementMembers() });
  const tasks = useQuery({ queryKey: ["requirements", requirementId, "tasks"], queryFn: () => listRequirementTasks(requirementId) });
  const assigned = participants.data?.find((p) => p.role === domain.role);
  const refresh = () => client.invalidateQueries();
  const create = useMutation({
    mutationFn: () => createStageTask(requirementId, { type, title: title.trim(), assigneeUserId: assignee! }),
    onSuccess: async () => {
      setTitle("");
      await refresh();
    },
    onError: (e) => Message.error(formatRequestError(e)),
  });
  const transition = useMutation({ mutationFn: ({ task, action }: { task: WorkItemDetail; action: string }) => transitionTask(task.id, action, task.version, reason.trim()), onSuccess: refresh, onError: (e) => Message.error(formatRequestError(e)) });
  const canCreate = assigned?.userId === user.data?.id && requirement.data?.status === domain.preparation;
  const items = tasks.data?.filter((task) => task.type === type) ?? [];
  return (
    <Card title={domain.label}>
      {tasks.isPending && <Spin />}
      {tasks.isError && <Alert type="error" content={formatRequestError(tasks.error)} />}
      {canCreate && (
        <Space wrap>
          <Input aria-label={`${domain.label}标题`} placeholder="任务标题" value={title} onChange={setTitle} maxLength={255} />
          <Select
            aria-label={`${domain.label}执行人`}
            placeholder="选择已关联执行人"
            style={{ minWidth: 180 }}
            value={assignee}
            onChange={setAssignee}
            options={(members.data ?? []).filter((member) => assigned?.executorUserIds?.includes(member.userId)).map((member) => ({ value: member.userId, label: member.displayName }))}
          />
          <Button type="primary" disabled={!title.trim() || !assignee} loading={create.isPending} onClick={() => create.mutate()}>
            创建{domain.label}
          </Button>
        </Space>
      )}
      {!items.length && <p>尚未创建{domain.label}，请由对应负责人在准备阶段创建并分配。</p>}
      {items.map((task) => (
        <div key={task.id} style={{ padding: "12px 0", borderBottom: "1px solid var(--color-border-2)" }}>
          <Space wrap>
            <code>{task.itemKey}</code>
            <strong>{task.title}</strong>
            <Tag>{statusLabel[task.status] ?? task.status}</Tag>
            <span>执行人：{members.data?.find((m) => m.userId === task.assigneeUserId)?.displayName ?? task.assigneeUserId}</span>
          </Space>
          <Space wrap style={{ marginTop: 8 }}>
            {task.availableActions.map((action) => (
              <Button key={action} loading={transition.isPending} disabled={(task.guardHints[action] ?? []).some((hint) => hint !== "reason") || (["REJECT", "BLOCK", "UNBLOCK"].includes(action) && !reason.trim())} onClick={() => transition.mutate({ task, action })}>
                {workflowActionLabel(action)}
              </Button>
            ))}
          </Space>
          {Object.values(task.guardHints)
            .flat()
            .filter((h) => h !== "reason").length > 0 && (
            <p>
              {[
                ...new Set(
                  Object.values(task.guardHints)
                    .flat()
                    .filter((h) => h !== "reason"),
                ),
              ]
                .map(guardHintLabel)
                .join("、")}
            </p>
          )}
        </div>
      ))}
      {items.some((task) => task.availableActions.some((a) => ["REJECT", "BLOCK", "UNBLOCK"].includes(a))) && <Input.TextArea aria-label="任务操作原因" placeholder="驳回、阻塞或解除阻塞时请填写原因" value={reason} onChange={setReason} style={{ marginTop: 12 }} />}
    </Card>
  );
}
