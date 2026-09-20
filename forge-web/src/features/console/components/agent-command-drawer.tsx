"use client";

import { Drawer, Message, Select, Tag } from "@arco-design/web-react";
import { IconFullscreen, IconPlus } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";

import { getOrganizationRequirement } from "@/features/work-item";
import { formatRequestError } from "@/lib/api";
import { useAgentPanelStore } from "@/stores/use-agent-panel-store";
import { createAgentConversation, listAgentConversations } from "../api/console-api";
import { parseRequirementId } from "../utils/requirement-context";
import { AgentConversationPanel } from "./agent-conversation-panel";
import styles from "./console.module.css";

export function AgentCommandDrawer() {
  const queryClient = useQueryClient();
  const pathname = usePathname() ?? "/";
  const open = useAgentPanelStore((state) => state.open);
  const setOpen = useAgentPanelStore((state) => state.setOpen);
  const activeId = useAgentPanelStore((state) => state.activeConversationId);
  const setActiveConversation = useAgentPanelStore((state) => state.setActiveConversation);
  const contextDismissed = useAgentPanelStore((state) => state.contextDismissed);
  const dismissContext = useAgentPanelStore((state) => state.dismissContext);
  const resetContext = useAgentPanelStore((state) => state.resetContext);
  /* 首次展开后保持挂载，关闭抽屉时 Run 仍能继续同步状态到顶栏 */
  const [mounted, setMounted] = useState(open);
  if (open && !mounted) setMounted(true);
  useEffect(() => {
    resetContext();
  }, [pathname, resetContext]);
  const conversations = useQuery({
    queryKey: ["agent-conversations"],
    queryFn: () => listAgentConversations(),
    enabled: mounted,
  });
  const create = useMutation({
    mutationFn: () => createAgentConversation(),
    onSuccess: (value) => {
      setActiveConversation(value.id);
      void queryClient.invalidateQueries({ queryKey: ["agent-conversations"] });
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const routeRequirementId = contextDismissed ? undefined : parseRequirementId(pathname);
  const requirement = useQuery({
    queryKey: ["requirement", routeRequirementId],
    queryFn: () => getOrganizationRequirement(routeRequirementId!),
    enabled: Boolean(routeRequirementId),
  });
  return (
    <Drawer
      width={460}
      visible={open}
      unmountOnExit={false}
      autoFocus={false}
      className={styles.agentDrawer}
      footer={null}
      bodyStyle={{ padding: 0, overflow: "hidden" }}
      onCancel={() => setOpen(false)}
      title={
        <div className={styles.drawerHead}>
          <b>Agent 指令</b>
          <button type="button" className={styles.drawerIconButton} aria-label="新建会话" disabled={create.isPending} onClick={() => create.mutate()}>
            <IconPlus />
          </button>
          <Link className={styles.drawerIconButton} href="/agent/command" aria-label="在完整页面打开" onClick={() => setOpen(false)}>
            <IconFullscreen />
          </Link>
        </div>
      }
    >
      {mounted && (
        <section className={styles.drawerPanel}>
          {routeRequirementId && (
            <Tag className={styles.contextTag} closable onClose={dismissContext}>
              当前需求 · {requirement.data ? `${requirement.data.itemKey} ${requirement.data.title}` : `#${routeRequirementId}`}
            </Tag>
          )}
          <AgentConversationPanel header={<SessionPicker conversations={conversations.data ?? []} activeId={activeId} onSelect={setActiveConversation} loading={conversations.isPending} />} contextRequirementId={routeRequirementId} />
        </section>
      )}
    </Drawer>
  );
}

function SessionPicker({ conversations, activeId, onSelect, loading }: { conversations: { id: number; title: string }[]; activeId?: number; onSelect: (id: number) => void; loading: boolean }) {
  return <Select className={styles.sessionPicker} aria-label="切换会话" loading={loading} placeholder="请选择或新建会话" value={activeId} onChange={(value) => onSelect(Number(value))} options={conversations.map((item) => ({ label: item.title, value: item.id }))} />;
}
