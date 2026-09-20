"use client";

import { Alert, Button, Message, Spin } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { formatRequestError } from "@/lib/api";
import { useAgentPanelStore } from "@/stores/use-agent-panel-store";
import { createAgentConversation, deleteAgentConversation, listAgentConversations, renameAgentConversation } from "../api/console-api";
import { ConversationActions } from "./conversation-actions";
import { AgentConversationPanel } from "./agent-conversation-panel";
import styles from "./console.module.css";

export function AgentCommandScreen() {
  const queryClient = useQueryClient();
  const storedId = useAgentPanelStore((state) => state.activeConversationId);
  const setActiveConversation = useAgentPanelStore((state) => state.setActiveConversation);
  const conversations = useQuery({
    queryKey: ["agent-conversations"],
    queryFn: () => listAgentConversations(),
  });
  const activeId = conversations.data?.some((item) => item.id === storedId) ? storedId : conversations.data?.[0]?.id;
  const create = useMutation({
    mutationFn: () => createAgentConversation(),
    onSuccess: (value) => {
      setActiveConversation(value.id);
      void queryClient.invalidateQueries({ queryKey: ["agent-conversations"] });
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const remove = useMutation({
    mutationFn: (id: number) => deleteAgentConversation(id),
    onSuccess: (_, deletedId) => {
      if (activeId === deletedId) {
        const remaining = conversations.data?.filter((item) => item.id !== deletedId) ?? [];
        setActiveConversation(remaining[0]?.id);
      }
      queryClient.removeQueries({ queryKey: ["agent-conversations", deletedId] });
      void queryClient.invalidateQueries({ queryKey: ["agent-conversations"] });
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const rename = useMutation({
    mutationFn: ({ id, title }: { id: number; title: string }) => renameAgentConversation(id, title),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["agent-conversations"] }),
    onError: (error) => Message.error(formatRequestError(error)),
  });
  return (
    <section className={styles.agentPage}>
      <header className={styles.pageHead}>
        <div>
          <h1>Agent 指令中心</h1>
          <p>向智能体下达指令，跟踪 Run 与工具调用</p>
        </div>
        <Button type="primary" loading={create.isPending} onClick={() => create.mutate()}>
          新建会话
        </Button>
      </header>
      {conversations.isError && <Alert type="error" content={formatRequestError(conversations.error)} />}
      <div className={styles.commandGrid}>
        <aside className={styles.sessionList}>
          <header>会话列表</header>
          {conversations.isPending && <Spin />}
          {conversations.data?.map((item) => (
            <div className={`${styles.sessionItem} ${item.id === activeId ? styles.selected : ""}`} key={item.id}>
              <button className={styles.sessionSelect} onClick={() => setActiveConversation(item.id)}>
                <span>
                  <b>{item.title}</b>
                </span>
              </button>
              <ConversationActions title={item.title} onRename={(title) => rename.mutateAsync({ id: item.id, title })} onDelete={() => remove.mutateAsync(item.id)} />
            </div>
          ))}
        </aside>
        <AgentConversationPanel />
      </div>
    </section>
  );
}
