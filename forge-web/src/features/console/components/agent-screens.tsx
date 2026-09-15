"use client";

import { Alert, Button, Empty, Message, Spin } from "@arco-design/web-react";
import { IconHistory, IconRobot } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { getCurrentUser } from "@/features/auth";
import { cancelAgentRun, cancelApproval, decideApproval, getAgentRun, getRunApproval, type ApprovalSnapshot } from "@/features/agent-run";
import { formatRequestError } from "@/lib/api";
import { createAgentConversation, listAgentConversations, listAgentMessages, sendAgentMessage } from "../api/console-api";
import { useConversationStream } from "../hooks/use-conversation-stream";
import { AgentMessage, ReasoningDetails } from "./agent-message";
import { ConversationComposer } from "./conversation-composer";
import styles from "./console.module.css";

export function AgentCommandScreen() {
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState<number>();
  const [draft, setDraft] = useState("");
  const [latestRunId, setLatestRunId] = useState<string>();
  const [latestRunConversationId, setLatestRunConversationId] = useState<number>();
  const [requirementId, setRequirementId] = useState<number>();
  const [latestSkill, setLatestSkill] = useState<string>();
  const conversations = useQuery({
    queryKey: ["agent-conversations"],
    queryFn: () => listAgentConversations(),
  });
  const activeId = selectedId ?? conversations.data?.[0]?.id;
  const currentUser = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
  const messages = useQuery({
    queryKey: ["agent-conversations", activeId, "messages"],
    queryFn: () => listAgentMessages(activeId!),
    enabled: Boolean(activeId),
  });
  const create = useMutation({
    mutationFn: () => createAgentConversation("新会话"),
    onSuccess: (value) => {
      setSelectedId(value.id);
      setLatestRunId(undefined);
      setLatestRunConversationId(undefined);
      setLatestSkill(undefined);
      setRequirementId(undefined);
      void queryClient.invalidateQueries({ queryKey: ["agent-conversations"] });
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const send = useMutation({
    mutationFn: () => sendAgentMessage(activeId!, draft, requirementId),
    onSuccess: (run) => {
      setLatestRunId(run.id);
      setLatestRunConversationId(activeId);
      setLatestSkill(run.skill);
      queryClient.setQueryData(["agent-run", run.id], run);
      setDraft("");
      void queryClient.invalidateQueries({
        queryKey: ["agent-conversations", activeId, "messages"],
      });
      void queryClient.invalidateQueries({ queryKey: ["agent-conversations"] });
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const active = conversations.data?.find((item) => item.id === activeId);
  const selectedRequirementId = active?.requirementId ?? requirementId;
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
  const stream = useConversationStream(currentUser.data?.organization.id, effectiveRunId);
  const isRunActive = Boolean(run.data && !run.data.terminal);
  const cancelRun = useMutation({
    mutationFn: () => cancelAgentRun(effectiveRunId!),
    onSuccess: (snapshot) => {
      queryClient.setQueryData(["agent-run", snapshot.id], snapshot);
      void queryClient.invalidateQueries({ queryKey: ["agent-conversations", activeId, "messages"] });
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const hasPersistedAgentAnswer = messages.data?.some((item) => item.sender === "AGENT" && item.runId === effectiveRunId);
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
          <header>
            会话 <span>{conversations.data?.length ?? 0}</span>
          </header>
          {conversations.isPending && <Spin />}
          {conversations.data?.map((item) => (
            <button
              className={item.id === activeId ? styles.selected : ""}
              key={item.id}
              onClick={() => {
                setSelectedId(item.id);
                setLatestRunId(undefined);
                setLatestRunConversationId(undefined);
                setLatestSkill(undefined);
                setRequirementId(undefined);
              }}
            >
              <IconRobot />
              <span>
                <b>{item.title}</b>
                <small>{new Date(item.updatedAt).toLocaleString("zh-CN")}</small>
              </span>
            </button>
          ))}
        </aside>
        <main className={styles.chatMain}>
          <header>
            <span className={styles.avatar}>AI</span>
            <div>
              <b>{active?.title ?? "请选择或新建会话"}</b>
              <small>ForgeAI Agent · 连续对话</small>
            </div>
          </header>
          <div className={styles.messages}>
            {messages.isPending && activeId && <Spin />}
            {!activeId && <Empty description="新建会话后即可开始对话" />}
            {messages.data?.map((item, index) =>
              item.sender === "USER" ? (
                <div className={styles.userMsg} key={`${item.createdAt}-${index}`}>
                  {item.body}
                </div>
              ) : (
                <AgentMessage title="ForgeAI Agent" key={`${item.createdAt}-${index}`}>
                  {item.runId === effectiveRunId && stream.reasoning.length > 0 && <ReasoningDetails reasoning={stream.reasoning} completed />}
                  <p>{item.body}</p>
                </AgentMessage>
              ),
            )}
            {effectiveRunId && !hasPersistedAgentAnswer && (stream.answer || isRunActive) && (
              <AgentMessage title="ForgeAI Agent">
                <ReasoningDetails reasoning={stream.reasoning} completed={Boolean(stream.answer)} />
                {stream.answer && (
                  <p className={styles.streamingAnswer} aria-live="polite">
                    {stream.answer}
                  </p>
                )}
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
        <aside className={styles.runPanel}>
          <header>Run 详情</header>
          <dl>
            <dt>状态</dt>
            <dd>
              <span className={styles.blueTag}>● {formatRunStatus(run.data?.status, Boolean(effectiveRunId))}</span>
            </dd>
            <dt>Run ID</dt>
            <dd>
              <code>{effectiveRunId ?? "—"}</code>
            </dd>
            <dt>智能体</dt>
            <dd>{latestSkill ? `${latestSkill} Agent` : "按 Requirement 阶段自动选择"}</dd>
          </dl>
          <Alert type="info" content="计划、Tool 调用、审批和结果以真实 Run 轨迹为准。" />
          <Button long disabled={!effectiveRunId} href={effectiveRunId ? `/agent/trace/${effectiveRunId}` : undefined}>
            <IconHistory />
            查看完整轨迹
          </Button>
        </aside>
      </div>
    </section>
  );
}

function formatRunStatus(status: string | undefined, hasRun: boolean) {
  const labels: Record<string, string> = {
    QUEUED: "排队中",
    RUNNING: "执行中",
    WAITING_APPROVAL: "等待审批",
    SUCCEEDED: "已完成",
    FAILED: "执行失败",
    CANCELLED: "已终止",
  };
  return status ? (labels[status] ?? status) : hasRun ? "已提交" : "等待指令";
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
