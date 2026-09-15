"use client";

import { Button, Input, Select } from "@arco-design/web-react";
import { IconCloseCircle, IconLink, IconSend } from "@arco-design/web-react/icon";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";

import { listOrganizationRequirements } from "@/features/work-item";
import styles from "./console.module.css";

interface BoundRequirement {
  id: number;
  itemKey: string | null;
  title: string | null;
}

export function ConversationComposer({
  activeConversation,
  boundRequirement,
  selectedRequirementId,
  onRequirementChange,
  draft,
  onDraftChange,
  running,
  sending,
  cancelling,
  onSend,
  onCancel,
}: {
  activeConversation: boolean;
  boundRequirement?: BoundRequirement;
  selectedRequirementId?: number;
  onRequirementChange: (requirementId?: number) => void;
  draft: string;
  onDraftChange: (value: string) => void;
  running: boolean;
  sending: boolean;
  cancelling: boolean;
  onSend: () => void;
  onCancel: () => void;
}) {
  const [search, setSearch] = useState("");
  const requirements = useQuery({
    queryKey: ["requirements", "conversation-picker", search],
    queryFn: () => listOrganizationRequirements({ q: search || undefined }),
    enabled: activeConversation && !boundRequirement,
  });
  const boundOption = boundRequirement ? [{ label: `${boundRequirement.itemKey ?? `#${boundRequirement.id}`} · ${boundRequirement.title ?? "当前需求"}`, value: boundRequirement.id }] : [];

  return (
    <footer className={styles.composer}>
      <Input.TextArea
        className={styles.composerInput}
        disabled={running}
        value={draft}
        onChange={onDraftChange}
        onKeyDown={(event) => {
          if (event.key !== "Enter" || event.shiftKey || event.nativeEvent.isComposing || running || sending || !activeConversation || !draft.trim()) return;
          event.preventDefault();
          onSend();
        }}
        autoSize={{ minRows: 3, maxRows: 6 }}
        placeholder={running ? "Agent 执行中，可点击右下角终止" : "描述你希望 Agent 完成的任务…"}
      />
      <div className={styles.composerToolbar}>
        <label className={styles.requirementPicker}>
          <IconLink />
          <span>当前需求</span>
          <Select
            aria-label="当前需求"
            allowClear
            disabled={running || Boolean(boundRequirement)}
            loading={requirements.isFetching}
            placeholder="选择需求"
            showSearch
            value={boundRequirement?.id ?? selectedRequirementId}
            onChange={(value) => onRequirementChange(typeof value === "number" ? value : undefined)}
            onSearch={setSearch}
            options={[...boundOption, ...(requirements.data?.items ?? []).map((item) => ({ label: `${item.itemKey} · ${item.title}`, value: item.id }))]}
          />
        </label>
        {running ? (
          <Button aria-label="终止 Agent" className={`${styles.composerAction} ${styles.stopAction}`} shape="circle" icon={<IconCloseCircle />} loading={cancelling} onClick={onCancel} />
        ) : (
          <Button aria-label="发送消息" className={styles.composerAction} type="primary" shape="circle" icon={<IconSend />} loading={sending} disabled={!activeConversation || !draft.trim()} onClick={onSend} />
        )}
      </div>
    </footer>
  );
}
