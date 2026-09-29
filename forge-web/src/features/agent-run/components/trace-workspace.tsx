import { Alert, Empty, Spin } from "@arco-design/web-react";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router";

import { getCurrentUser } from "@/features/auth";
import { listAgentConversations } from "@/features/console";
import { formatRequestError } from "@/lib/api";
import { listConversationRuns } from "../api/agent-run-api";
import { useRunTraceRefresh } from "../hooks/use-run-trace-refresh";
import { formatNodeTime } from "../model/trace-model";
import { TraceTurn } from "./trace-turn";
import styles from "./trace.module.css";

/* 所选会话的轮次列表：按 Run 创建时间升序渲染，最新一轮默认展开。 */
function ConversationTrace({ conversationId, organizationId }: { conversationId: number; organizationId: number | undefined }) {
  /* 已展开的 Run ID 集合；历史轮次默认折叠，展开时才加载轨迹。 */
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const seededRunId = useRef<string | undefined>(undefined);
  const runs = useQuery({
    queryKey: ["conversation-runs", conversationId],
    queryFn: () => listConversationRuns(conversationId),
    /* 轮次列表存在非终态 Run 时以 5 秒轮询兜底；全部进入终态后自动停止。 */
    refetchInterval: (query) => (query.state.data?.some((run) => !run.terminal) ? 5000 : false),
  });
  /* 接口已按创建时间升序返回；保留后端顺序，仅在需要时读取首尾。 */
  const orderedRuns = runs.data ?? [];
  const latestRunId = orderedRuns.at(-1)?.runId;
  /* 取最近一个未进入终态的 Run，用于订阅实时事件与轮询兜底。 */
  const activeRun = orderedRuns.filter((run) => !run.terminal).at(-1);

  useRunTraceRefresh(organizationId, activeRun);

  /* 首次拿到该会话的轮次时默认展开最新一轮，后续刷新不重置用户展开状态。 */
  useEffect(() => {
    if (!latestRunId || seededRunId.current === latestRunId) {
      return;
    }
    seededRunId.current = latestRunId;
    setExpanded(new Set([latestRunId]));
  }, [latestRunId]);

  const toggle = (runId: string) =>
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(runId)) {
        next.delete(runId);
      } else {
        next.add(runId);
      }
      return next;
    });

  if (runs.isPending) {
    return <Spin />;
  }
  if (runs.isError) {
    return <Alert type="error" content={formatRequestError(runs.error)} />;
  }
  if (orderedRuns.length === 0) {
    return <Empty description="所选会话尚未产生任何执行轮次。" />;
  }
  return (
    <>
      {orderedRuns.map((run, index) => (
        <TraceTurn key={run.runId} run={run} index={index + 1} expanded={expanded.has(run.runId)} onToggle={() => toggle(run.runId)} />
      ))}
    </>
  );
}

/* 执行轨迹工作台：左侧本人会话列表，右侧所选会话的完整多轮轨迹。 */
export function AgentTraceWorkspace() {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedId = Number(searchParams.get("conversationId"));
  const user = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
  const conversations = useQuery({
    queryKey: ["agent-conversations"],
    queryFn: () => listAgentConversations(),
  });
  const list = conversations.data;
  /* 无参数或参数指向不存在/非本人会话时，回退到最近更新的会话（接口已倒序）。 */
  const activeId = list?.length ? (list.some((item) => item.id === requestedId) ? requestedId : list[0].id) : undefined;

  useEffect(() => {
    if (activeId === undefined || activeId === requestedId) {
      return;
    }
    setSearchParams({ conversationId: String(activeId) }, { replace: true });
  }, [activeId, requestedId, setSearchParams]);

  return (
    <section className={styles.page}>
      <header className={styles.pageHead}>
        <div>
          <h1>执行轨迹</h1>
          <p>按会话查看每一轮 Run 的模型思考、工具调用、审批与终态。</p>
        </div>
      </header>
      <div className={styles.workspace}>
        <aside className={styles.sidebar}>
          <header className={styles.sidebarHeader}>会话列表</header>
          {conversations.isPending && <Spin />}
          {conversations.isError && <Alert type="error" content={formatRequestError(conversations.error)} />}
          {list?.length === 0 && <Empty description="还没有任何会话。" />}
          {list?.map((item) => (
            <button key={item.id} type="button" className={`${styles.sessionItem} ${item.id === activeId ? styles.sessionItemActive : ""}`} aria-pressed={item.id === activeId} onClick={() => setSearchParams({ conversationId: String(item.id) })}>
              <b className={styles.sessionTitle}>{item.title}</b>
              <span className={styles.sessionRequirement}>{item.requirementKey ? `${item.requirementKey} · ${item.requirementTitle ?? ""}` : "未绑定需求"}</span>
              <time className={styles.sessionUpdated}>{formatNodeTime(item.updatedAt) ?? ""}</time>
            </button>
          ))}
        </aside>
        <main className={styles.traceMain}>{conversations.isPending ? <Spin /> : activeId === undefined ? <Empty description="暂无会话，请先在 Agent 指令中心创建会话。" /> : <ConversationTrace key={activeId} conversationId={activeId} organizationId={user.data?.organization.id} />}</main>
      </div>
    </section>
  );
}
