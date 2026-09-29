import { Button, Dropdown, Input, Menu, Message, Modal } from "@arco-design/web-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { completeDevTask, startDevelopment, transitionTask } from "@/features/work-item";
import { formatRequestError } from "@/lib/api";
import { guardHintLabel, workflowActionLabel } from "@/lib/labels";
import type { BoardItem } from "../api/console-api";
import styles from "./task-board.module.css";

/* 需要填写原因才能提交的任务动作。 */
const reasonActions = ["REJECT", "BLOCK", "UNBLOCK"];

/**
 * 看板卡片的任务操作区：只投影服务端返回的可执行动作，不做本地状态判断。
 * 开发任务的开工与提交验证分别走创建分支/MR 与校验 MR 已合并的专用接口，其余动作走通用流转。
 */
export function TaskBoardActions({ item }: { item: BoardItem }) {
  const client = useQueryClient();
  const [menuVisible, setMenuVisible] = useState(false);
  const [reasonAction, setReasonAction] = useState<string>();
  const [reason, setReason] = useState("");
  const refresh = () => void client.invalidateQueries({ queryKey: ["task-board"] });
  const onError = (error: unknown) => Message.error(formatRequestError(error));
  const start = useMutation({ mutationFn: () => startDevelopment({ organizationId: 0, taskId: item.id, idempotencyKey: crypto.randomUUID() }), onSuccess: refresh, onError });
  const complete = useMutation({ mutationFn: () => completeDevTask({ organizationId: 0, taskId: item.id, expectedVersion: item.version }), onSuccess: refresh, onError });
  const transition = useMutation({ mutationFn: ({ action, value }: { action: string; value?: string }) => transitionTask(item.id, action, item.version, value), onSuccess: refresh, onError });

  /* 动作实际调用的写操作决定该动作的错误处理与加载态。 */
  const execute = (action: string, value?: string) => {
    if (item.type === "DEV_TASK" && action === "START") return start.mutate();
    if (item.type === "DEV_TASK" && action === "SUBMIT_REVIEW") return complete.mutate();
    transition.mutate({ action, value });
  };
  /* 动作缺失的准入材料去重提示；reason 在弹窗内填写，不在卡片上提示。 */
  const missing = [...new Set(item.availableActions.flatMap((action) => (item.guardHints[action] ?? []).filter((hint) => hint !== "reason")))];
  const isBlocked = (action: string) => (item.guardHints[action] ?? []).some((hint) => hint !== "reason");

  if (!item.availableActions.length) return null;

  return (
    <div className={styles.cardActions}>
      <Dropdown
        trigger="click"
        popupVisible={menuVisible}
        onVisibleChange={setMenuVisible}
        droplist={
          <Menu
            onClickMenuItem={(key) => {
              setMenuVisible(false);
              if (reasonActions.includes(key)) {
                setReason("");
                setReasonAction(key);
                return;
              }
              execute(key);
            }}
          >
            {item.availableActions.map((action) => (
              <Menu.Item key={action} disabled={isBlocked(action)}>
                {workflowActionLabel(action)}
              </Menu.Item>
            ))}
          </Menu>
        }
      >
        <Button size="mini" loading={start.isPending || complete.isPending || transition.isPending} aria-label={`任务操作：${item.itemKey}`}>
          任务操作
        </Button>
      </Dropdown>
      {missing.length > 0 && <p className={styles.cardHint}>还缺：{missing.map(guardHintLabel).join("、")}</p>}
      <Modal
        title={workflowActionLabel(reasonAction ?? "")}
        visible={reasonAction !== undefined}
        okText="提交"
        cancelText="取消"
        confirmLoading={transition.isPending}
        okButtonProps={{ disabled: !reason.trim() }}
        onCancel={() => setReasonAction(undefined)}
        onOk={() => {
          execute(reasonAction!, reason.trim());
          setReasonAction(undefined);
        }}
      >
        <Input.TextArea aria-label="任务操作原因" placeholder="请填写本次操作的原因" value={reason} onChange={setReason} maxLength={500} />
      </Modal>
    </div>
  );
}
