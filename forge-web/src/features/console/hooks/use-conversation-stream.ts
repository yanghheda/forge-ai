import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";

import { agentEventUrl, type AgentRunSnapshot } from "@/features/agent-run";
import { useTypewriterText } from "./use-typewriter-text";

interface StreamEvent {
  sequence: number;
  type: string;
  payload: Record<string, unknown>;
}

/* 与轨迹页保持一致的关注事件集合：任一到达都说明轨迹可能变化，触发去抖刷新内联过程节点。 */
const TRACE_EVENT_TYPES = [
  "agent.queued",
  "agent.started",
  "context.ready",
  "plan.created",
  "step.started",
  "step.completed",
  "step.failed",
  "thinking.delta",
  "reasoning.delta",
  "tool.requested",
  "tool.started",
  "tool.completed",
  "tool.failed",
  "message.delta",
  "message.completed",
  "approval.required",
  "approval.approved",
  "approval.rejected",
  "approval.expired",
  "agent.completed",
  "agent.failed",
  "agent.cancelled",
];

/* 终态事件：到达后关闭订阅并立即刷新一次缓存。 */
const TERMINAL_EVENT_TYPES = ["agent.completed", "agent.failed", "agent.cancelled"];

/*
 * 订阅进行中 Run 的 SSE 事件：累积流式回答文本，缓存 Run 状态变化，
 * 并在轨迹相关事件到达时去抖刷新该 Run 的内联轨迹节点。
 * SSE 出错时静默重连，页面由轮询兜底，不向上抛出错误。
 */
export function useConversationStream(organizationId: number | undefined, runId: string | undefined) {
  const queryClient = useQueryClient();
  const [answerState, setAnswerState] = useState({ runId: "", value: "" });
  const [connected, setConnected] = useState(false);
  const cursor = useRef(0);
  const completeAnswer = answerState.runId === runId ? answerState.value : "";
  const displayedAnswer = useTypewriterText(completeAnswer, runId ?? "");

  useEffect(() => {
    cursor.current = 0;
    if (!organizationId || !runId) return;
    let source: EventSource | undefined;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let debounce: ReturnType<typeof setTimeout> | undefined;
    let stopped = false;

    const scheduleTraceRefresh = () => {
      if (debounce) {
        return;
      }
      debounce = setTimeout(() => {
        debounce = undefined;
        void queryClient.invalidateQueries({ queryKey: ["run-trace", runId] });
      }, 300);
    };

    const connect = () => {
      source = new EventSource(agentEventUrl(organizationId, runId, cursor.current), { withCredentials: true });
      source.onopen = () => setConnected(true);
      const receive = (raw: Event) => {
        const event = JSON.parse((raw as MessageEvent<string>).data) as StreamEvent;
        if (event.sequence <= cursor.current) return;
        cursor.current = event.sequence;
        if (event.type === "message.delta") {
          setAnswerState((current) => ({
            runId,
            value: (current.runId === runId ? current.value : "") + String(event.payload.delta ?? ""),
          }));
        }
        if (event.type === "approval.required") {
          queryClient.setQueryData<AgentRunSnapshot>(["agent-run", runId], (current) => (current ? { ...current, status: "WAITING_APPROVAL" } : current));
        }
        if (event.type === "agent.started") {
          queryClient.setQueryData<AgentRunSnapshot>(["agent-run", runId], (current) => (current ? { ...current, status: "RUNNING" } : current));
        }
        if (TERMINAL_EVENT_TYPES.includes(event.type)) {
          const status = String(event.payload.status ?? "");
          queryClient.setQueryData<AgentRunSnapshot>(["agent-run", runId], (current) => (current ? { ...current, status, terminal: true } : current));
          stopped = true;
          setConnected(false);
          source?.close();
          /* 终态落定后立即刷新一次轨迹，避免去抖延迟或遗漏最后一次增量。 */
          void queryClient.invalidateQueries({ queryKey: ["run-trace", runId] });
          void queryClient.invalidateQueries({ queryKey: ["agent-run", runId] });
          void queryClient.invalidateQueries({ queryKey: ["agent-conversations"] });
          return;
        }
        scheduleTraceRefresh();
      };
      for (const type of TRACE_EVENT_TYPES) {
        source.addEventListener(type, receive);
      }
      source.onerror = () => {
        setConnected(false);
        source?.close();
        if (!stopped) retry = setTimeout(connect, 500);
      };
    };

    connect();
    return () => {
      stopped = true;
      setConnected(false);
      source?.close();
      if (retry) clearTimeout(retry);
      if (debounce) clearTimeout(debounce);
    };
  }, [organizationId, queryClient, runId]);

  return {
    answer: displayedAnswer,
    hasAnswer: completeAnswer.length > 0,
    isTyping: displayedAnswer !== completeAnswer,
    connected,
  };
}
