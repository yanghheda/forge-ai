import { Input, Message, Modal, Select } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { createStageTask } from "@/features/work-item";
import { formatRequestError } from "@/lib/api";
import { listTaskCreationOptions } from "../api/console-api";

/* 任务类型在创建入口的展示文案，与看板类型标签保持一致。 */
const taskTypeLabels = { UX_TASK: "设计任务", DEV_TASK: "开发任务", QA_TASK: "测试任务" } as const;

/** 看板创建任务的弹窗：需求与任务类型由服务端准备态决定，执行人只能是该需求已关联的执行人。 */
export function TaskCreateModal({ visible, onClose }: { visible: boolean; onClose: () => void }) {
  const client = useQueryClient();
  const [requirementId, setRequirementId] = useState<number>();
  const [assignee, setAssignee] = useState<number>();
  const [title, setTitle] = useState("");
  const options = useQuery({ queryKey: ["task-board-creation-options"], queryFn: () => listTaskCreationOptions(), enabled: visible });
  const selected = options.data?.find((option) => option.requirementId === requirementId);
  const create = useMutation({
    mutationFn: () => createStageTask(requirementId!, { type: selected!.taskType, title: title.trim(), assigneeUserId: assignee! }),
    onSuccess: async () => {
      setRequirementId(undefined);
      setAssignee(undefined);
      setTitle("");
      await Promise.all([client.invalidateQueries({ queryKey: ["task-board"] }), client.invalidateQueries({ queryKey: ["task-board-creation-options"] })]);
      onClose();
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });

  return (
    <Modal title="创建任务" visible={visible} okText="创建任务" cancelText="取消" confirmLoading={create.isPending} okButtonProps={{ disabled: !selected || !assignee || !title.trim() }} onCancel={onClose} onOk={() => create.mutate()}>
      <Select
        aria-label="需求"
        placeholder={options.isPending ? "正在加载可创建任务的需求…" : "选择需求"}
        style={{ width: "100%", marginBottom: 12 }}
        value={requirementId}
        onChange={(value) => {
          setRequirementId(value as number);
          /* 需求变化后执行人候选联动刷新，清空上一次选择。 */
          setAssignee(undefined);
        }}
        options={(options.data ?? []).map((option) => ({ value: option.requirementId, label: `${option.requirementItemKey} ${option.requirementTitle} · ${taskTypeLabels[option.taskType]}` }))}
        notFoundContent={options.isPending ? null : "当前没有可创建任务的需求"}
      />
      <Select
        aria-label="执行人"
        placeholder={selected ? (selected.executors.length ? "选择已关联执行人" : "该需求尚未关联本阶段执行人") : "请先选择需求"}
        style={{ width: "100%", marginBottom: 12 }}
        disabled={!selected}
        value={assignee}
        onChange={(value) => setAssignee(value as number)}
        options={(selected?.executors ?? []).map((executor) => ({ value: executor.userId, label: executor.displayName }))}
      />
      <Input aria-label="任务标题" placeholder="任务标题" value={title} onChange={setTitle} maxLength={255} />
    </Modal>
  );
}
