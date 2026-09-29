import { useState } from "react";

import type { TraceNode } from "../api/agent-run-api";
import { formatDuration, formatNodeTime, nodePresentation, readNumber, readText, readTextList, statusLabel, type NodePresentation } from "../model/trace-model";
import styles from "./trace.module.css";

/* 保留对状态标签的转发导出，避免既有导入方（如 trace-turn）改动。 */
export { statusLabel } from "../model/trace-model";

/* 将毫秒耗时格式化为中文文案；不足 1 秒按毫秒，其余按秒（非整秒保留一位小数）。 */
function formatMillis(ms: number): string {
  if (ms < 1000) {
    return `${ms} 毫秒`;
  }
  const seconds = ms / 1000;
  return Number.isInteger(seconds) ? `${seconds} 秒` : `${seconds.toFixed(1)} 秒`;
}

/* 折叠文本：默认只显示前两行预览，展开后展示完整文本并保留换行。 */
function CollapsibleText({ text, emptyText }: { text: string | null; emptyText: string }) {
  const [expanded, setExpanded] = useState(false);
  const hasText = Boolean(text && text.trim().length > 0);
  if (!hasText) {
    return <p className={styles.nodeMuted}>{emptyText}</p>;
  }
  return (
    <div className={styles.collapsible}>
      <p className={`${styles.text} ${expanded ? "" : styles.textCollapsed}`}>{text}</p>
      <button type="button" className={styles.textToggle} onClick={() => setExpanded((value) => !value)}>
        {expanded ? "收起" : "展开全部"}
      </button>
    </div>
  );
}

/* 结构化 JSON 展示块；值缺失时退化为说明文案，保证不丢信息也不报错。 */
function JsonBlock({ label, value, emptyText, testId }: { label: string; value: unknown; emptyText: string; testId?: string }) {
  const hasValue = value !== undefined && value !== null;
  return (
    <div className={styles.jsonBlock}>
      <span className={styles.jsonLabel}>{label}</span>
      {hasValue ? (
        <pre className={styles.json} data-testid={testId}>
          {JSON.stringify(value, null, 2)}
        </pre>
      ) : (
        <p className={styles.nodeMuted}>{emptyText}</p>
      )}
    </div>
  );
}

/* 单条键值字段，用于审批、步骤与终态等结构化节点。 */
function Field({ label, value }: { label: string; value: string }) {
  return (
    <>
      <dt className={styles.fieldLabel}>{label}</dt>
      <dd className={styles.fieldValue}>{value}</dd>
    </>
  );
}

/* 计算 TOOL 节点摘要行：工具名 · 风险等级 · 状态 · 耗时。 */
function toolSummaryLine(node: TraceNode): string {
  const detail = node.detail ?? {};
  const pieces = [readText(detail, "toolName") ?? node.title ?? "工具调用"];
  const riskLevel = readText(detail, "riskLevel");
  if (riskLevel) {
    pieces.push(`风险 ${riskLevel}`);
  }
  const status = statusLabel(node.status ?? readText(detail, "status"));
  if (status) {
    pieces.push(status);
  }
  const duration = formatDuration(node.startedAt, node.finishedAt);
  if (duration) {
    pieces.push(`耗时 ${duration}`);
  }
  return pieces.filter(Boolean).join(" · ");
}

/* 按节点 kind 渲染正文；未知 kind 统一走 EVENT 兜底，展示完整 detail JSON。 */
function NodeBody({ node, presentation }: { node: TraceNode; presentation: NodePresentation }) {
  const { detail, text } = presentation;
  switch (presentation.kind) {
    case "THINKING":
      return <CollapsibleText text={text} emptyText="该节点暂无可展示的模型思考内容。" />;
    case "REASONING":
      return <CollapsibleText text={text} emptyText="该节点暂无可展示的执行思路。" />;
    case "ANSWER":
      return text && text.trim().length > 0 ? <p className={styles.text}>{text}</p> : <p className={styles.nodeMuted}>尚未产生回答内容。</p>;
    case "PLAN": {
      const steps = readTextList(detail, "steps");
      return steps.length > 0 ? (
        <ol className={styles.steps}>
          {steps.map((step, index) => (
            <li key={`${index}-${step}`}>{step}</li>
          ))}
        </ol>
      ) : (
        <p className={styles.nodeMuted}>计划未提供步骤明细。</p>
      );
    }
    case "STEP": {
      const stepNo = readNumber(detail, "stepNo");
      const stepType = readText(detail, "type");
      const stepName = readText(detail, "name");
      const summary = readText(detail, "summary");
      const errorCode = readText(detail, "errorCode");
      const duration = formatDuration(node.startedAt, node.finishedAt);
      return (
        <dl className={styles.fields}>
          {stepNo !== null && <Field label="步骤序号" value={`第 ${stepNo} 步`} />}
          {stepType && <Field label="步骤类型" value={stepType} />}
          {stepName && <Field label="步骤名称" value={stepName} />}
          {summary && <Field label="摘要" value={summary} />}
          {errorCode && <Field label="错误码" value={errorCode} />}
          {duration && <Field label="耗时" value={duration} />}
        </dl>
      );
    }
    case "TOOL":
      return (
        <>
          <JsonBlock label="参数" value={detail.arguments} emptyText="该工具调用未记录参数。" testId="tool-arguments" />
          <JsonBlock label="结果" value={detail.result} emptyText="该工具调用尚未产生结果。" testId="tool-result" />
        </>
      );
    case "APPROVAL": {
      const approvalId = readText(detail, "approvalId");
      const toolName = readText(detail, "toolName");
      const riskLevel = readText(detail, "riskLevel");
      const status = statusLabel(readText(detail, "status"));
      const reason = readText(detail, "reason");
      const decidedBy = readText(detail, "decidedBy");
      const expiresAt = readText(detail, "expiresAt");
      return (
        <dl className={styles.fields}>
          {approvalId && <Field label="审批 ID" value={approvalId} />}
          {toolName && <Field label="工具" value={toolName} />}
          {riskLevel && <Field label="风险等级" value={riskLevel} />}
          {status && <Field label="状态" value={status} />}
          {reason && <Field label="原因" value={reason} />}
          {decidedBy && <Field label="决策人" value={decidedBy} />}
          {expiresAt && <Field label="过期时间" value={formatNodeTime(expiresAt) ?? expiresAt} />}
        </dl>
      );
    }
    case "TERMINAL": {
      const status = statusLabel(readText(detail, "status"));
      const summary = readText(detail, "summary");
      const errorCode = readText(detail, "errorCode");
      const durationMs = readNumber(detail, "durationMs");
      return (
        <dl className={styles.fields}>
          {status && <Field label="最终状态" value={status} />}
          {summary && <Field label="摘要" value={summary} />}
          {errorCode && <Field label="错误码" value={errorCode} />}
          {durationMs !== null && <Field label="总耗时" value={formatMillis(durationMs)} />}
        </dl>
      );
    }
    default:
      return <JsonBlock label={`事件详情（${presentation.rawKind}）`} value={detail} emptyText="该事件没有附加内容。" testId="event-detail" />;
  }
}

/* 轨迹节点卡片：按 nodePresentation 分派展示形态，TOOL / EVENT 折叠正文，其余直接展开。 */
export function TraceNodeCard({ node }: { node: TraceNode }) {
  const presentation = nodePresentation(node);
  const time = formatNodeTime(node.startedAt);
  const duration = formatDuration(node.startedAt, node.finishedAt);
  const status = statusLabel(node.status);
  const isTool = presentation.kind === "TOOL";
  const collapsible = isTool || presentation.kind === "EVENT";

  const head = (
    <div className={styles.nodeHead}>
      <span className={styles.nodeDot} data-icon={presentation.icon} />
      <span className={styles.nodeTitle}>{isTool ? toolSummaryLine(node) : presentation.title}</span>
      <span className={styles.nodeMeta}>
        {!isTool && status && (
          <span className={styles.statusBadge} data-status={node.status ?? undefined}>
            {status}
          </span>
        )}
        {time && <time>{time}</time>}
        {!isTool && duration && <span>耗时 {duration}</span>}
      </span>
    </div>
  );

  return (
    <article className={styles.node} data-kind={presentation.kind}>
      {collapsible ? (
        <details className={styles.nodeDetails}>
          <summary className={styles.nodeSummary}>{head}</summary>
          <NodeBody node={node} presentation={presentation} />
        </details>
      ) : (
        <>
          {head}
          <NodeBody node={node} presentation={presentation} />
        </>
      )}
    </article>
  );
}
