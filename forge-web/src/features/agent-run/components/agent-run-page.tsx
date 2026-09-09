"use client";

import { Alert, Button, Card, Space, Spin, Steps, Typography } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { getCurrentUser } from "@/features/auth";
import { listProjects } from "@/features/project";
import {
  agentEventUrl,
  decideApproval,
  getAgentRun,
  getRunApproval,
  type AgentRunSnapshot,
  type ApprovalSnapshot,
} from "../api/agent-run-api";
import {
  initialRunTimelineState,
  reduceRunEvent,
  type AgentEventEnvelope,
  type RunTimelineState,
} from "../model/run-reducer";
import ui from "@/components/workbench/workbench.module.css";

const eventTypes = [
  "agent.queued",
  "agent.started",
  "context.ready",
  "plan.created",
  "step.started",
  "step.completed",
  "step.failed",
  "tool.requested",
  "tool.started",
  "tool.completed",
  "tool.failed",
  "approval.required",
  "approval.approved",
  "approval.rejected",
  "approval.expired",
  "agent.completed",
  "agent.failed",
  "agent.cancelled",
];

function useRunTimeline(
  workspaceId: number,
  projectId: number,
  snapshot: AgentRunSnapshot,
): RunTimelineState {
  const [state, setState] = useState<RunTimelineState>(() => ({
    ...initialRunTimelineState(snapshot.lastSequence),
    connectionState: snapshot.terminal ? "closed" : "connecting",
    steps: Object.fromEntries(snapshot.steps.map((step) => [step.stepNo, step])),
    final: snapshot.terminal
      ? { status: snapshot.status, errorCode: snapshot.errorCode ?? undefined }
      : undefined,
  }));
  const stateRef = useRef(state);

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  useEffect(() => {
    if (snapshot.terminal) {
      return;
    }
    let source: EventSource | undefined;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let stopped = false;

    const connect = () => {
      source = new EventSource(
        agentEventUrl(workspaceId, projectId, snapshot.id, stateRef.current.lastSequence),
        { withCredentials: true },
      );
      source.onopen = () => setState((current) => ({ ...current, connectionState: "open" }));
      const receive = (raw: Event) => {
        const message = raw as MessageEvent<string>;
        const event = JSON.parse(message.data) as AgentEventEnvelope;
        const next = reduceRunEvent(stateRef.current, event);
        stateRef.current = next;
        setState(next);
        if (next.connectionState === "gap" || next.connectionState === "closed") {
          source?.close();
          if (next.connectionState === "gap" && !stopped) {
            retry = setTimeout(connect, 100);
          }
        }
      };
      for (const type of eventTypes) {
        source.addEventListener(type, receive);
      }
      source.onerror = () => {
        source?.close();
        if (!stopped) {
          setState((current) => ({ ...current, connectionState: "disconnected" }));
          retry = setTimeout(connect, 500);
        }
      };
    };

    connect();
    return () => {
      stopped = true;
      source?.close();
      if (retry) clearTimeout(retry);
    };
  }, [workspaceId, projectId, snapshot.id, snapshot.terminal]);

  return state;
}

export function AgentRunPage({
  workspaceId,
  projectId,
  runId,
}: {
  workspaceId: number;
  projectId: number;
  runId: string;
}) {
  const snapshot = useQuery({
    queryKey: ["agent-run", runId],
    queryFn: () => getAgentRun(workspaceId, projectId, runId),
  });
  if (snapshot.isPending) return <Spin tip="正在加载 Agent 运行…" />;
  if (!snapshot.data) return <Alert type="error" content="Agent 运行不存在或无权访问。" />;
  return <AgentRunTimeline workspaceId={workspaceId} projectId={projectId} snapshot={snapshot.data} />;
}

function AgentRunTimeline({
  workspaceId,
  projectId,
  snapshot,
}: {
  workspaceId: number;
  projectId: number;
  snapshot: AgentRunSnapshot;
}) {
  const timeline = useRunTimeline(workspaceId, projectId, snapshot);
  const steps = Object.values(timeline.steps).sort((left, right) => left.stepNo - right.stepNo);
  return (
    <section className={ui.page}>
      <header className={ui.pageHeader}>
        <div>
          <span className={ui.eyebrow}>Agent 执行</span>
          <h1>Agent 运行</h1>
          <p>查看执行步骤、实时事件与人工审批。</p>
        </div>
      </header>
      <Typography.Paragraph copyable>{snapshot.id}</Typography.Paragraph>
      <Card className={ui.panel} title={`状态：${timeline.final?.status ?? snapshot.status}`}>
        <div className={ui.content}>
        <Typography.Text type="secondary">
          连接：{timeline.connectionState} · 连续事件序号：{timeline.lastSequence}
        </Typography.Text>
        {timeline.connectionState === "gap" && (
          <Alert type="warning" content="检测到事件跳号，正在从最后连续序号恢复。" />
        )}
        <Steps direction="vertical" current={steps.length}>
          {steps.map((step) => (
            <Steps.Step
              key={step.stepNo}
              title={`${step.stepNo}. ${step.name}`}
              description={`${step.status}${step.summary ? ` · ${step.summary}` : ""}`}
            />
          ))}
        </Steps>
        </div>
      </Card>
      {snapshot.status === "WAITING_APPROVAL" && (
        <ApprovalCard workspaceId={workspaceId} projectId={projectId} runId={snapshot.id} />
      )}
    </section>
  );
}

function ApprovalCard({
  workspaceId,
  projectId,
  runId,
}: {
  workspaceId: number;
  projectId: number;
  runId: string;
}) {
  const queryClient = useQueryClient();
  const approval = useQuery({
    queryKey: ["agent-approval", runId],
    queryFn: () => getRunApproval(workspaceId, projectId, runId),
  });
  const decide = useMutation({
    mutationFn: ({ snapshot, decision }: { snapshot: ApprovalSnapshot; decision: "APPROVE" | "REJECT" }) =>
      decideApproval(snapshot, workspaceId, projectId, decision),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["agent-approval", runId] });
      void queryClient.invalidateQueries({ queryKey: ["agent-run", runId] });
    },
  });
  if (approval.isPending) return <Spin tip="加载审批事实…" />;
  if (!approval.data) return <Alert type="error" content="审批不存在或当前账户无权访问。" />;
  const snapshot = approval.data;
  return (
    <Card title={`审批：${snapshot.toolName} v${snapshot.toolVersion}`}>
      <Typography.Paragraph>{snapshot.reason}</Typography.Paragraph>
      <Typography.Paragraph>风险：{snapshot.riskLevel} · 状态：{snapshot.status}</Typography.Paragraph>
      <Typography.Paragraph copyable>参数摘要：{snapshot.argumentHash}</Typography.Paragraph>
      <Typography.Paragraph>过期时间：{new Date(snapshot.expiresAt).toLocaleString()}</Typography.Paragraph>
      {snapshot.resources.map((resource) => (
        <Typography.Paragraph key={`${resource.type}:${resource.id}`}>
          影响资源：{resource.type} {resource.id}，冻结版本 {resource.version}
        </Typography.Paragraph>
      ))}
      {snapshot.status === "PENDING" && (
        <Space>
          <Button
            type="primary"
            loading={decide.isPending}
            onClick={() => decide.mutate({ snapshot, decision: "APPROVE" })}
          >
            批准并恢复
          </Button>
          <Button
            status="danger"
            loading={decide.isPending}
            onClick={() => decide.mutate({ snapshot, decision: "REJECT" })}
          >
            拒绝
          </Button>
        </Space>
      )}
      {decide.isError && <Alert type="error" content="审批决定未提交，请刷新后重试。" />}
    </Card>
  );
}

export function AgentRunRoute({
  workspaceSlug,
  projectKey,
  runId,
}: {
  workspaceSlug: string;
  projectKey: string;
  runId: string;
}) {
  const user = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
  const workspace = user.data?.workspaces.find((item) => item.slug === workspaceSlug);
  const projects = useQuery({
    queryKey: ["projects", workspace?.id],
    queryFn: () => listProjects(workspace!.id),
    enabled: !!workspace,
  });
  const project = projects.data?.find((item) => item.key === projectKey);
  if (user.isPending || projects.isPending) return <Spin />;
  if (!workspace || !project) return <Alert type="error" content="项目不存在或当前账户无权访问。" />;
  return <AgentRunPage workspaceId={workspace.id} projectId={project.id} runId={runId} />;
}
