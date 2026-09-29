import { Alert, Empty, Spin } from "@arco-design/web-react";
import { useQuery } from "@tanstack/react-query";

import { getRunTrace, type ConversationRun } from "../api/agent-run-api";
import { formatDuration, formatNodeTime } from "../model/trace-model";
import { TraceNodeCard, statusLabel } from "./trace-node-card";
import styles from "./trace.module.css";

/* Skill 角色取值到中文展示名的映射；未知取值原样保留。 */
const SKILL_LABELS: Record<string, string> = {
  PRODUCT: "产品",
  UX: "设计",
  DEVELOPER: "研发",
  QA: "测试",
  RELEASE: "发布",
};

interface TraceTurnProps {
  /* 本轮 Run 摘要 */
  run: ConversationRun;
  /* 该轮在会话中的序号，从 1 开始 */
  index: number;
  /* 是否展开正文 */
  expanded: boolean;
  /* 切换展开状态 */
  onToggle: () => void;
}

/* 单轮 Run：头部汇总指令与状态，展开时才按需加载并渲染该轮轨迹节点。 */
export function TraceTurn({ run, index, expanded, onToggle }: TraceTurnProps) {
  const trace = useQuery({
    queryKey: ["run-trace", run.runId],
    queryFn: () => getRunTrace(run.runId),
    /* 懒加载：仅在展开时拉取轨迹，结果由 react-query 缓存。 */
    enabled: expanded,
    /* 非终态轮次在展开时以 5 秒轮询兜底，终态后停止。 */
    refetchInterval: expanded && !run.terminal ? 5000 : false,
  });
  const startedAt = formatNodeTime(run.startedAt);
  const duration = formatDuration(run.startedAt, run.finishedAt);
  const status = statusLabel(run.status);
  return (
    <article className={styles.turn} data-terminal={run.terminal ? "true" : "false"}>
      <button type="button" className={styles.turnHead} aria-expanded={expanded} onClick={onToggle}>
        <span className={styles.turnTitle}>
          <span className={styles.turnIndex}>第 {index} 轮</span>
          <p className={styles.turnCommand}>{run.triggerMessage ?? "本轮未记录用户指令。"}</p>
        </span>
        <span className={styles.turnMeta}>
          <span className={styles.skillBadge}>{SKILL_LABELS[run.skill] ?? run.skill}</span>
          {status && (
            <span className={styles.statusBadge} data-status={run.status}>
              {status}
            </span>
          )}
          {startedAt && <time>{startedAt}</time>}
          {duration && <span>耗时 {duration}</span>}
          {!run.terminal && <span className={styles.liveIndicator}>实时刷新中</span>}
          <span className={styles.chevron}>{expanded ? "收起" : "展开"}</span>
        </span>
      </button>
      {expanded && (
        <div className={styles.turnBody}>
          {trace.isPending && <Spin />}
          {trace.isError && <Alert type="error" content="轨迹加载失败，请稍后重试。" />}
          {trace.data && trace.data.nodes.length === 0 && <Empty description="该轮尚未产生轨迹节点。" />}
          {trace.data?.nodes.map((node) => (
            <TraceNodeCard key={node.key} node={node} />
          ))}
        </div>
      )}
    </article>
  );
}
