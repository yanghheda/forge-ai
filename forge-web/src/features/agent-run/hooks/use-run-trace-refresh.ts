import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef } from "react";

import { agentEventUrl, type ConversationRun } from "../api/agent-run-api";

/* 轨迹页关注的运行事件类型；任一到达都说明轨迹可能变化，触发去抖刷新。 */
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

/*
 * 订阅进行中 Run 的 SSE 事件：事件到达后去抖刷新该 Run 的轨迹详情与轮次列表，
 * 便于轨迹页即时看到新增节点；Run 进入终态后不再订阅。
 * SSE 出错时静默重连，页面退化为轮询兜底，不向上抛出错误。
 */
export function useRunTraceRefresh(organizationId: number | undefined, run: ConversationRun | undefined) {
  const queryClient = useQueryClient();
  /* 存放最新 Run 快照，供重连时读取游标，避免游标变化导致连接反复重建。 */
  const runRef = useRef(run);
  const runId = run?.runId;
  const terminal = run?.terminal ?? true;

  useEffect(() => {
    runRef.current = run;
  }, [run]);

  useEffect(() => {
    if (!organizationId || !runId || terminal) {
      return;
    }
    let source: EventSource | undefined;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let debounce: ReturnType<typeof setTimeout> | undefined;
    let stopped = false;

    const scheduleRefresh = () => {
      if (debounce) {
        return;
      }
      debounce = setTimeout(() => {
        debounce = undefined;
        void queryClient.invalidateQueries({ queryKey: ["run-trace", runId] });
        void queryClient.invalidateQueries({ queryKey: ["conversation-runs"] });
      }, 300);
    };

    const connect = () => {
      source = new EventSource(agentEventUrl(organizationId, runId, runRef.current?.lastSequence ?? 0), { withCredentials: true });
      const receive = () => scheduleRefresh();
      for (const type of TRACE_EVENT_TYPES) {
        source.addEventListener(type, receive);
      }
      /* 连接中断时不报错，仅关闭连接并稍后重连，页面由 5 秒轮询兜底。 */
      source.onerror = () => {
        source?.close();
        if (!stopped) {
          retry = setTimeout(connect, 500);
        }
      };
    };

    connect();
    return () => {
      stopped = true;
      source?.close();
      if (retry) {
        clearTimeout(retry);
      }
      if (debounce) {
        clearTimeout(debounce);
      }
    };
  }, [organizationId, runId, terminal, queryClient]);
}
