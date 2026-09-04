"use client";

import { Alert, Card, Spin, Steps, Typography } from "@arco-design/web-react";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { getCurrentUser } from "@/features/auth";
import { listProjects } from "@/features/project";
import { agentEventUrl, getAgentRun, type AgentRunSnapshot } from "../api/agent-run-api";
import {
  initialRunTimelineState,
  reduceRunEvent,
  type AgentEventEnvelope,
  type RunTimelineState,
} from "../model/run-reducer";

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
  if (snapshot.isPending) return <Spin tip="加载 Agent Run…" />;
  if (!snapshot.data) return <Alert type="error" content="Agent Run 不存在或无权访问。" />;
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
    <section>
      <Typography.Title heading={2}>Agent Run</Typography.Title>
      <Typography.Paragraph copyable>{snapshot.id}</Typography.Paragraph>
      <Card title={`状态：${timeline.final?.status ?? snapshot.status}`}>
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
      </Card>
    </section>
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
