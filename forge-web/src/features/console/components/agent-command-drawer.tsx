import { Drawer, Message, Select, Tag } from "@arco-design/web-react";
import { IconFullscreen, IconPlus } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Link, useLocation } from "react-router";

import { getOrganizationRequirement } from "@/features/work-item";
import { formatRequestError } from "@/lib/api";
import { useAgentPanelStore, type AgentContext } from "@/stores/use-agent-panel-store";
import { createAgentConversation, listAgentConversations } from "../api/console-api";
import { parseAgentContext } from "../utils/requirement-context";
import { AgentConversationPanel } from "./agent-conversation-panel";
import styles from "./console.module.css";

export function AgentCommandDrawer() {
  const queryClient = useQueryClient();
  const { pathname } = useLocation();
  const open = useAgentPanelStore((state) => state.open);
  const setOpen = useAgentPanelStore((state) => state.setOpen);
  const activeId = useAgentPanelStore((state) => state.activeConversationId);
  const setActiveConversation = useAgentPanelStore((state) => state.setActiveConversation);
  const contextDismissed = useAgentPanelStore((state) => state.contextDismissed);
  const explicitContext = useAgentPanelStore((state) => state.context);
  const dismissContext = useAgentPanelStore((state) => state.dismissContext);
  const resetContext = useAgentPanelStore((state) => state.resetContext);
  /* 首次展开后保持挂载，关闭抽屉时 Run 仍能继续同步状态到顶栏 */
  const [mounted, setMounted] = useState(open);
  if (open && !mounted) setMounted(true);
  /* 只在路由真正变化时清理上下文，避免抽屉挂载时清掉列表页刚交给 Agent 的工作项 */
  const lastPath = useRef(pathname);
  useEffect(() => {
    if (lastPath.current === pathname) return;
    lastPath.current = pathname;
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
  /* 显式上下文优先于路由上下文；用户手动解绑后两者同时失效 */
  const context = contextDismissed ? undefined : (explicitContext ?? parseAgentContext(pathname));
  const requirementId = context?.kind === "requirement" ? context.id : undefined;
  const requirement = useQuery({
    queryKey: ["requirement", requirementId],
    queryFn: () => getOrganizationRequirement(requirementId!),
    enabled: Boolean(requirementId),
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
          <Link className={styles.drawerIconButton} to="/agent/command" aria-label="在完整页面打开" onClick={() => setOpen(false)}>
            <IconFullscreen />
          </Link>
        </div>
      }
    >
      {mounted && (
        <section className={styles.drawerPanel}>
          {context && (
            <Tag className={styles.contextTag} closable onClose={dismissContext}>
              {contextLabel(context, requirement.data)}
            </Tag>
          )}
          <AgentConversationPanel header={<SessionPicker conversations={conversations.data ?? []} activeId={activeId} onSelect={setActiveConversation} loading={conversations.isPending} />} context={context} />
        </section>
      )}
    </Drawer>
  );
}

/* 上下文标签：需求优先用最新详情，其余类型回退到页面提供的可读名称。 */
function contextLabel(context: AgentContext, requirement?: { itemKey: string; title: string }): string {
  if (context.kind === "requirement") return `当前需求 · ${requirement ? `${requirement.itemKey} ${requirement.title}` : (context.label ?? `#${context.id}`)}`;
  if (context.kind === "defect") return `当前缺陷 · ${context.label ?? `#${context.id}`}`;
  return `当前任务 · ${context.label ?? `#${context.id}`}`;
}

function SessionPicker({ conversations, activeId, onSelect, loading }: { conversations: { id: number; title: string }[]; activeId?: number; onSelect: (id: number) => void; loading: boolean }) {
  return <Select className={styles.sessionPicker} aria-label="切换会话" loading={loading} placeholder="请选择或新建会话" value={activeId} onChange={(value) => onSelect(Number(value))} options={conversations.map((item) => ({ label: item.title, value: item.id }))} />;
}
