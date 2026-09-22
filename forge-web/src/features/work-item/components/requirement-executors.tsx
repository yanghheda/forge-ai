"use client";

import { Button, Card, Message, Select, Space, Tag } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { getCurrentUser } from "@/features/auth";
import { formatRequestError } from "@/lib/api";
import { getRequirementParticipants, listRequirementMembers, replaceRequirementExecutors, type RequirementParticipant } from "../api/work-item-api";
import { roleMeta } from "../utils/requirement-detail-display";

export function RequirementExecutors({ requirementId, status }: { requirementId: number; status: string }) {
  const user = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
  const members = useQuery({ queryKey: ["requirements", "members"], queryFn: () => listRequirementMembers() });
  const participants = useQuery({ queryKey: ["requirements", requirementId, "participants"], queryFn: () => getRequirementParticipants(requirementId) });
  return (
    <Card title="领域执行人及评审进度">
      {(participants.data ?? []).map((participant) => (
        <ExecutorRow
          key={`${participant.role}-${participant.executorUserIds?.join(",")}`}
          requirementId={requirementId}
          participant={participant}
          names={members.data ?? []}
          editable={participant.userId === user.data?.id && ["DRAFT", "REQUIREMENT_APPROVAL", "READY_FOR_REVIEW"].includes(status)}
          reviewing={status === "PRODUCT_REVIEW"}
        />
      ))}
    </Card>
  );
}

function ExecutorRow({ requirementId, participant, names, editable, reviewing }: { requirementId: number; participant: RequirementParticipant; names: Array<{ userId: number; displayName: string; roles: string[] }>; editable: boolean; reviewing: boolean }) {
  const client = useQueryClient();
  const [selected, setSelected] = useState(participant.executorUserIds ?? []);
  const save = useMutation({ mutationFn: () => replaceRequirementExecutors(requirementId, participant.role, selected), onSuccess: () => client.invalidateQueries({ queryKey: ["requirements", requirementId] }), onError: (error) => Message.error(formatRequestError(error)) });
  return (
    <div style={{ marginBottom: 16 }}>
      <p>
        {roleMeta[participant.role].label}：{participant.displayName} {reviewing && <Tag color={participant.reviewApproved ? "green" : "gray"}>{participant.reviewApproved ? "评审已通过" : "待确认"}</Tag>}
      </p>
      {editable ? (
        <Space direction="vertical" style={{ width: "100%" }}>
          <Select
            mode="multiple"
            aria-label={`${roleMeta[participant.role].label}分配执行人`}
            placeholder="选择执行人（至少一名）"
            value={selected}
            onChange={setSelected}
            options={names.filter((m) => m.roles.includes(participant.role) || m.roles.includes(`${participant.role}_ADMIN`)).map((m) => ({ value: m.userId, label: m.displayName }))}
          />
          <Button loading={save.isPending} onClick={() => save.mutate()}>
            保存执行人
          </Button>
        </Space>
      ) : (
        <p>执行人：{(participant.executorUserIds ?? []).map((id) => names.find((m) => m.userId === id)?.displayName ?? `#${id}`).join("、") || "尚未分配"}</p>
      )}
    </div>
  );
}
