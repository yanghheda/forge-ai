"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";

import { agentEventUrl, type AgentRunSnapshot } from "@/features/agent-run";
import { useTypewriterText } from "./use-typewriter-text";

interface StreamEvent {
  sequence: number;
  type: string;
  payload: Record<string, unknown>;
}

export function useConversationStream(organizationId: number | undefined, runId: string | undefined) {
  const queryClient = useQueryClient();
  const [answerState, setAnswerState] = useState({ runId: "", value: "" });
  const [reasoningState, setReasoningState] = useState({ runId: "", values: [] as string[] });
  const [connected, setConnected] = useState(false);
  const cursor = useRef(0);
  const completeAnswer = answerState.runId === runId ? answerState.value : "";
  const displayedAnswer = useTypewriterText(completeAnswer, runId ?? "");

  useEffect(() => {
    cursor.current = 0;
    if (!organizationId || !runId) return;
    let source: EventSource | undefined;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let stopped = false;

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
        if (event.type === "reasoning.delta") {
          setReasoningState((current) => ({
            runId,
            values: [...(current.runId === runId ? current.values : []), String(event.payload.delta ?? "")],
          }));
        }
        if (event.type === "approval.required") {
          queryClient.setQueryData<AgentRunSnapshot>(["agent-run", runId], (current) => (current ? { ...current, status: "WAITING_APPROVAL" } : current));
        }
        if (event.type === "agent.started") {
          queryClient.setQueryData<AgentRunSnapshot>(["agent-run", runId], (current) => (current ? { ...current, status: "RUNNING" } : current));
        }
        if (["agent.completed", "agent.failed", "agent.cancelled"].includes(event.type)) {
          const status = String(event.payload.status ?? "");
          queryClient.setQueryData<AgentRunSnapshot>(["agent-run", runId], (current) => (current ? { ...current, status, terminal: true } : current));
          stopped = true;
          setConnected(false);
          source?.close();
          void queryClient.invalidateQueries({ queryKey: ["agent-run", runId] });
          void queryClient.invalidateQueries({ queryKey: ["agent-conversations"] });
        }
      };
      for (const type of ["agent.started", "reasoning.delta", "message.delta", "approval.required", "agent.completed", "agent.failed", "agent.cancelled"]) {
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
    };
  }, [organizationId, queryClient, runId]);

  return {
    answer: displayedAnswer,
    reasoning: reasoningState.runId === runId ? reasoningState.values : [],
    connected,
  };
}
