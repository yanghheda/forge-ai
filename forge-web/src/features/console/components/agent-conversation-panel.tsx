"use client";

import { Alert, Button, Empty, Message, Spin } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useEffect, useState } from "react";

import { cancelAgentRun, cancelApproval, decideApproval, getAgentRun, getRunApproval, type ApprovalSnapshot } from "@/features/agent-run";
import { getCurrentUser } from "@/features/auth";
import { formatRequestError } from "@/lib/api";
import { useAgentPanelStore } from "@/stores/use-agent-panel-store";
import { listAgentConversations, listAgentMessages, sendAgentMessage } from "../api/console-api";
import { useConversationStream } from "../hooks/use-conversation-stream";
import { isLiveRunPlaybackEnabled, isStreamPlaybackVisible } from "../utils/conversation-answer-playback";
import { AgentMessage, ReasoningDetails } from "./agent-message";
import { AgentMessageBody } from "./agent-message-body";
import { ConversationComposer } from "./conversation-composer";
import styles from "./console.module.css";

export function AgentConversationPanel({ header, contextRequirementId }: { header?: ReactNode; contextRequirementId?: number }) {
  const queryClient = useQueryClient();
  const storedId = useAgentPanelStore((state) => state.activeConversationId);
  const setActiveConversation = useAgentPanelStore((state) => state.setActiveConversation);
  const setRunState = useAgentPanelStore((state) => state.setRunState);
  const [draft, setDraft] = useState("");
  const [latestRunId, setLatestRunId] = useState<string>();
  const [latestRunConversationId, setLatestRunConversationId] = useState<number>();
  const [requirementId, setRequirementId] = useState<number>();
  const conversations = useQuery({
    queryKey: ["agent-conversations"],
    queryFn: () => listAgentConversations(),
  });
  const currentUser = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
  const activeId = conversations.data?.some((item) => item.id === storedId) ? storedId : conversations.data?.[0]?.id;
  useEffect(() => {
    if (activeId !== storedId) setActiveConversation(activeId);
  }, [activeId, storedId, setActiveConversation]);
  const active = conversations.data?.find((item) => item.id === activeId);
  const selectedRequirementId = active?.requirementId ?? requirementId ?? contextRequirementId;
  const messages = useQuery({
    queryKey: ["agent-conversations", activeId, "messages"],
    queryFn: () => listAgentMessages(activeId!),
    enabled: Boolean(activeId),
  });
  const send = useMutation({
    mutationFn: () => sendAgentMessage(activeId!, draft, selectedRequirementId),
    onSuccess: (run) => {
      setLatestRunId(run.id);
      setLatestRunConversationId(activeId);
      queryClient.setQueryData(["agent-run", run.id], run);
      setDraft("");
      void queryClient.invalidateQueries({
        queryKey: ["agent-conversations", activeId, "messages"],
      });
      void queryClient.invalidateQueries({ queryKey: ["agent-conversations"] });
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const effectiveRunId =
    (latestRunConversationId === activeId ? latestRunId : undefined) ??
    messages.data
      ?.slice()
      .reverse()
      .find((item) => Boolean(item.runId))?.runId ??
    undefined;
  const run = useQuery({
    queryKey: ["agent-run", effectiveRunId],
    queryFn: () => getAgentRun(currentUser.data!.organization.id, effectiveRunId!),
    enabled: Boolean(effectiveRunId && currentUser.data),
  });
  const isRunActive = Boolean(run.data && !run.data.terminal);
  useEffect(() => {
    setRunState({ running: isRunActive, awaitingApproval: run.data?.status === "WAITING_APPROVAL" });
  }, [isRunActive, run.data?.status, setRunState]);
  const livePlaybackEnabled = isLiveRunPlaybackEnabled({ activeConversationId: activeId, effectiveRunId, isRunActive, latestRunConversationId, latestRunId });
  const stream = useConversationStream(currentUser.data?.organization.id, livePlaybackEnabled ? effectiveRunId : undefined);
  const cancelRun = useMutation({
    mutationFn: () => cancelAgentRun(effectiveRunId!),
    onSuccess: (snapshot) => {
      queryClient.setQueryData(["agent-run", snapshot.id], snapshot);
      void queryClient.invalidateQueries({ queryKey: ["agent-conversations", activeId, "messages"] });
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const hasPersistedAgentAnswer = messages.data?.some((item) => item.sender === "AGENT" && item.runId === effectiveRunId);
  const streamPlaybackVisible = isStreamPlaybackVisible(stream, Boolean(hasPersistedAgentAnswer));
  return (
    <main className={styles.chatMain}>
      <header>{header ?? <b>{active?.title ?? "请选择或新建会话"}</b>}</header>
      <div className={styles.messages}>
        {messages.isPending && activeId && <Spin />}
        {!activeId && <Empty description="新建会话后即可开始对话" />}
        {messages.data?.map((item, index) =>
          item.sender === "AGENT" && item.runId === effectiveRunId && streamPlaybackVisible ? null : item.sender === "USER" ? (
            <div className={styles.userMsg} key={`${item.createdAt}-${index}`}>
              {item.body}
            </div>
          ) : (
            <AgentMessage title="ForgeAI Agent" key={`${item.createdAt}-${index}`}>
              {item.runId === effectiveRunId && stream.reasoning.length > 0 && <ReasoningDetails reasoning={stream.reasoning} completed />}
              <AgentMessageBody content={item.body} />
            </AgentMessage>
          ),
        )}
        {effectiveRunId && (streamPlaybackVisible || (!hasPersistedAgentAnswer && isRunActive)) && (
          <AgentMessage title="ForgeAI Agent">
            <ReasoningDetails reasoning={stream.reasoning} completed={Boolean(stream.answer)} />
            {stream.answer && <AgentMessageBody content={stream.answer} streaming />}
          </AgentMessage>
        )}
        {run.data?.status === "WAITING_APPROVAL" && currentUser.data && <ConversationApproval organizationId={currentUser.data.organization.id} runId={run.data.id} currentUserId={currentUser.data.id} />}
      </div>
      <ConversationComposer
        activeConversation={Boolean(activeId)}
        boundRequirement={active?.requirementId ? { id: active.requirementId, itemKey: active.requirementKey, title: active.requirementTitle } : undefined}
        selectedRequirementId={selectedRequirementId}
        onRequirementChange={setRequirementId}
        draft={draft}
        onDraftChange={setDraft}
        running={isRunActive}
        sending={send.isPending}
        cancelling={cancelRun.isPending}
        onSend={() => send.mutate()}
        onCancel={() => cancelRun.mutate()}
      />
    </main>
  );
}

function ConversationApproval({ organizationId, runId, currentUserId }: { organizationId: number; runId: string; currentUserId: number }) {
  const queryClient = useQueryClient();
  const approval = useQuery({ queryKey: ["agent-approval", runId], queryFn: () => getRunApproval(organizationId, runId) });
  const decide = useMutation({
    mutationFn: ({ snapshot, decision }: { snapshot: ApprovalSnapshot; decision: "APPROVE" | "REJECT" }) => decideApproval(snapshot, organizationId, decision),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["agent-approval", runId] });
      void queryClient.invalidateQueries({ queryKey: ["agent-run", runId] });
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const cancel = useMutation({
    mutationFn: (snapshot: ApprovalSnapshot) => cancelApproval(snapshot, organizationId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["agent-approval", runId] });
      void queryClient.invalidateQueries({ queryKey: ["agent-run", runId] });
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  if (approval.isPending) return <Spin tip="加载待审批操作…" />;
  if (!approval.data) return <Alert type="error" content="待审批操作不可访问。" />;
  const snapshot = approval.data;
  const isRequester = snapshot.requestedBy === currentUserId;
  return (
    <AgentMessage title={`待审批：${snapshot.toolName}`}>
      <p>{snapshot.reason}</p>
      <p>
        风险：{snapshot.riskLevel} · 状态：{snapshot.status}
      </p>
      {snapshot.status === "PENDING" && isRequester && snapshot.riskLevel === "MEDIUM" && (
        <>
          <Alert type="warning" content="Agent 请求代你执行 MEDIUM 风险操作，请确认参数摘要后继续。" />
          <Button type="primary" loading={decide.isPending} onClick={() => decide.mutate({ snapshot, decision: "APPROVE" })}>
            确认执行
          </Button>
          <Button status="danger" loading={cancel.isPending} onClick={() => cancel.mutate(snapshot)}>
            取消申请
          </Button>
        </>
      )}
      {snapshot.status === "PENDING" && isRequester && snapshot.riskLevel === "HIGH" && (
        <>
          <Alert type="warning" content="HIGH 风险操作必须由另一名具有审批权限的成员处理。" />
          <Button status="danger" loading={cancel.isPending} onClick={() => cancel.mutate(snapshot)}>
            取消申请
          </Button>
        </>
      )}
      {snapshot.status === "PENDING" && !isRequester && (
        <>
          <Button type="primary" loading={decide.isPending} onClick={() => decide.mutate({ snapshot, decision: "APPROVE" })}>
            批准并恢复
          </Button>
          <Button status="danger" loading={decide.isPending} onClick={() => decide.mutate({ snapshot, decision: "REJECT" })}>
            拒绝
          </Button>
        </>
      )}
    </AgentMessage>
  );
}
