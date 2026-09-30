import { IconBulb, IconCheckCircle, IconCloseCircle, IconFile, IconInfoCircle, IconLoading, IconThunderbolt } from "@arco-design/web-react/icon";
import type { ReactNode } from "react";

import { formatDuration, nodePresentation, readNumber, readText, readTextList, statusLabel, type NodePresentation, type TraceNode } from "@/features/agent-run";

import styles from "./console.module.css";

/*
 * 聊天流内联展示的执行过程节点类型：
 * ANSWER 由消息正文单独承载，TERMINAL 由面板头部状态承载，故不在此重复渲染。
 */
const PROCESS_KINDS = new Set(["THINKING", "REASONING", "PLAN", "STEP", "TOOL", "APPROVAL", "EVENT"]);

/* 尚未落定的状态集合：用于把图标渲染为进行中，而不是误判为已完成。 */
const RUNNING_STATUSES = new Set(["RUNNING", "PENDING", "IN_PROGRESS", "WAITING_APPROVAL", "PENDING_APPROVAL"]);

/* 已判定失败或终止的状态集合：用于把图标渲染为失败态。 */
const FAILED_STATUSES = new Set(["FAILED", "REJECTED", "EXPIRED", "CANCELLED"]);

/* 连续的工具调用聚为一组，折叠后以一个「已调用 N 个工具」摘要行概括。 */
interface ToolGroup {
  /* 分组判别标识，与单节点区分渲染分支。 */
  kind: "TOOL_GROUP";
  /* 组内按事件顺序排列的工具调用节点。 */
  nodes: TraceNode[];
}

/* 单节点条目：携带已归一化的展示信息，避免重复计算。 */
interface NodeItem {
  /* 分组判别标识，与工具分组区分渲染分支。 */
  kind: "NODE";
  /* 原始轨迹节点，用于读取状态与时间。 */
  node: TraceNode;
  /* 归一化后的展示信息（类型、标题、正文、detail）。 */
  presentation: NodePresentation;
}

type ProcessItem = NodeItem | ToolGroup;

/* 将结构化值转为多行 JSON 文本；值缺失时返回空串，由调用方回退到占位文案。 */
function toPrettyJson(value: unknown): string {
  return value === undefined || value === null ? "" : JSON.stringify(value, null, 2);
}

/* 读取节点状态：优先取聚合节点状态，其次取 detail 中的状态字段。 */
function nodeStatus(node: TraceNode, detail: Record<string, unknown>): string | null {
  return node.status ?? readText(detail, "status");
}

/* 状态图标：进行中转圈、失败打叉、其余按已完成处理。 */
function StatusIcon({ status }: { status: string | null }) {
  if (status && FAILED_STATUSES.has(status)) {
    return <IconCloseCircle className={styles.processIcon} />;
  }
  if (status && RUNNING_STATUSES.has(status)) {
    return <IconLoading className={styles.processIcon} spin />;
  }
  return <IconCheckCircle className={styles.processIcon} />;
}

/* 子项头部统一布局：图标 + 标签 + 可选尾随元信息。 */
function ItemHead({ icon, label, meta }: { icon: ReactNode; label: ReactNode; meta?: ReactNode }) {
  return (
    <span className={styles.processItemHead}>
      {icon}
      <span className={styles.processItemLabel}>{label}</span>
      {meta && <span className={styles.processMeta}>{meta}</span>}
    </span>
  );
}

/* 工具分组摘要：概括调用数量，并在存在失败或进行中时补充计数。 */
function toolGroupSummary(nodes: TraceNode[]): string {
  const failed = nodes.filter((node) => FAILED_STATUSES.has(nodeStatus(node, node.detail) ?? "")).length;
  const running = nodes.filter((node) => RUNNING_STATUSES.has(nodeStatus(node, node.detail) ?? "")).length;
  const parts = [`已调用 ${nodes.length} 个工具`];
  if (failed > 0) {
    parts.push(`失败 ${failed}`);
  }
  if (running > 0) {
    parts.push(`进行中 ${running}`);
  }
  return parts.join(" · ");
}

/* 模型思考 / 执行思路：二级折叠块，正文保留换行，无正文时给出进行中占位。 */
function TextItem({ presentation }: { presentation: NodePresentation }) {
  const hasText = Boolean(presentation.text && presentation.text.trim().length > 0);
  return (
    <details className={styles.processItem} open>
      <summary className={styles.processItemHead}>
        <IconBulb className={styles.processIcon} />
        <span className={styles.processItemLabel}>{presentation.title}</span>
      </summary>
      {hasText ? <p className={styles.processText}>{presentation.text}</p> : <p className={styles.processMuted}>正在分析上下文并形成执行计划…</p>}
    </details>
  );
}

/* 执行计划：展开的步骤列表，无步骤时给出占位说明。 */
function PlanItem({ presentation }: { presentation: NodePresentation }) {
  const steps = readTextList(presentation.detail, "steps");
  return (
    <div className={styles.processItem} data-kind="PLAN">
      <ItemHead icon={<IconFile className={styles.processIcon} />} label={presentation.title} />
      {steps.length > 0 ? (
        <ol className={styles.processSteps}>
          {steps.map((step, index) => (
            <li key={`${index}-${step}`}>{step}</li>
          ))}
        </ol>
      ) : (
        <p className={styles.processMuted}>计划未提供步骤明细。</p>
      )}
    </div>
  );
}

/* 执行步骤：单行摘要，尾随状态与耗时。 */
function StepItem({ node, presentation }: { node: TraceNode; presentation: NodePresentation }) {
  const detail = presentation.detail;
  const stepNo = readNumber(detail, "stepNo");
  const name = readText(detail, "name");
  const status = nodeStatus(node, detail);
  const label = [stepNo !== null ? `第 ${stepNo} 步` : presentation.title, name].filter((piece): piece is string => Boolean(piece)).join(" · ");
  const meta = [statusLabel(status), formatDuration(node.startedAt, node.finishedAt)].filter((piece): piece is string => Boolean(piece)).join(" · ");
  return (
    <div className={styles.processItem} data-kind="STEP">
      <ItemHead icon={<StatusIcon status={status} />} label={label} meta={meta || undefined} />
    </div>
  );
}

/* 工具调用：命令行风格卡片，折叠正文展示参数与结果 JSON。 */
function ToolRow({ node }: { node: TraceNode }) {
  const status = nodeStatus(node, node.detail);
  const toolName = readText(node.detail, "toolName") ?? node.title ?? "工具调用";
  const duration = formatDuration(node.startedAt, node.finishedAt);
  const args = toPrettyJson(node.detail.arguments);
  const result = toPrettyJson(node.detail.result);
  const meta = [statusLabel(status) ?? "已调用", duration].filter((piece): piece is string => Boolean(piece)).join(" · ");
  return (
    <details className={styles.processTool} data-kind="TOOL">
      <summary className={styles.processToolHead}>
        <StatusIcon status={status} />
        <span className={styles.processItemLabel}>{meta}</span>
        <code className={styles.processToolName}>{toolName}</code>
      </summary>
      <div className={styles.processKv}>
        <span className={styles.processKvLabel}>参数</span>
        {args ? (
          <pre className={styles.processJson} data-testid="tool-arguments">
            {args}
          </pre>
        ) : (
          <p className={styles.processMuted}>该工具调用未记录参数。</p>
        )}
        <span className={styles.processKvLabel}>结果</span>
        {result ? (
          <pre className={styles.processJson} data-testid="tool-result">
            {result}
          </pre>
        ) : (
          <p className={styles.processMuted}>该工具调用尚未产生结果。</p>
        )}
      </div>
    </details>
  );
}

/* 工具分组：折叠块，摘要行概括调用数量，正文逐条渲染工具卡片。 */
function ToolGroupItem({ nodes }: { nodes: TraceNode[] }) {
  return (
    <details className={styles.processItem} open>
      <summary className={styles.processItemHead}>
        <IconThunderbolt className={styles.processIcon} />
        <span className={styles.processItemLabel}>{toolGroupSummary(nodes)}</span>
      </summary>
      <div className={styles.processToolList}>
        {nodes.map((node) => (
          <ToolRow key={node.key} node={node} />
        ))}
      </div>
    </details>
  );
}

/* 审批决策：折叠块，摘要行展示工具、风险与状态，正文展示决策字段。 */
function ApprovalItem({ node, presentation }: { node: TraceNode; presentation: NodePresentation }) {
  const detail = presentation.detail;
  const toolName = readText(detail, "toolName") ?? node.title ?? "审批决策";
  const rawStatus = readText(detail, "status");
  const reason = readText(detail, "reason");
  const decidedBy = readText(detail, "decidedBy");
  const approvalId = readText(detail, "approvalId");
  const riskLevel = readText(detail, "riskLevel");
  const hasFields = Boolean(reason || decidedBy || approvalId);
  const meta = (
    <>
      {riskLevel && <span className={styles.processBadge}>风险 {riskLevel}</span>}
      {statusLabel(rawStatus) && (
        <span className={styles.processBadge} data-status={rawStatus ?? undefined}>
          {statusLabel(rawStatus)}
        </span>
      )}
    </>
  );
  return (
    <details className={styles.processItem} data-kind="APPROVAL" open={hasFields}>
      <summary className={styles.processItemHead}>
        <IconInfoCircle className={styles.processIcon} />
        <span className={styles.processItemLabel}>{toolName}</span>
        <span className={styles.processMeta}>{meta}</span>
      </summary>
      {hasFields && (
        <dl className={styles.processFields}>
          {reason && (
            <>
              <dt className={styles.processKvLabel}>原因</dt>
              <dd className={styles.processKvValue}>{reason}</dd>
            </>
          )}
          {decidedBy && (
            <>
              <dt className={styles.processKvLabel}>决策人</dt>
              <dd className={styles.processKvValue}>{decidedBy}</dd>
            </>
          )}
          {approvalId && (
            <>
              <dt className={styles.processKvLabel}>审批 ID</dt>
              <dd className={styles.processKvValue}>{approvalId}</dd>
            </>
          )}
        </dl>
      )}
    </details>
  );
}

/* 未知类型兜底：折叠块展示完整 detail JSON，保留后端原始类型与标题信息。 */
function EventItem({ presentation }: { presentation: NodePresentation }) {
  return (
    <details className={styles.processItem} data-kind="EVENT">
      <summary className={styles.processItemHead}>
        <IconInfoCircle className={styles.processIcon} />
        <span className={styles.processItemLabel}>{presentation.title}</span>
      </summary>
      <pre className={styles.processJson} data-testid="event-detail">
        {JSON.stringify(presentation.detail, null, 2)}
      </pre>
    </details>
  );
}

/* 按事件顺序把节点整理为渲染条目：连续的工具调用合并为一个分组，其余按节点类型原序输出。 */
function buildItems(nodes: TraceNode[]): ProcessItem[] {
  const items: ProcessItem[] = [];
  for (const node of nodes) {
    const presentation = nodePresentation(node);
    if (!PROCESS_KINDS.has(presentation.kind)) {
      continue;
    }
    if (presentation.kind !== "TOOL") {
      items.push({ kind: "NODE", node, presentation });
      continue;
    }
    const last = items[items.length - 1];
    if (last && last.kind === "TOOL_GROUP") {
      last.nodes.push(node);
    } else {
      items.push({ kind: "TOOL_GROUP", nodes: [node] });
    }
  }
  return items;
}

/*
 * 聊天流内联执行过程：整体为一个默认收起的「思考过程」区块，
 * 内部按节点类型渲染思考、思路、计划、步骤、工具分组、审批与兜底事件。
 * 全部被过滤或入参为空时返回 null，避免在气泡内留下空白区块。
 * running 表示该 Run 尚未进入终态：标题显示「思考中」并挂扫光动画；
 * Run 结束后改为展示本轮耗时 duration，耗时缺失时回退为区块名。
 */
export function AgentProcessNodes({ nodes, running = false, duration = null }: { nodes: TraceNode[]; running?: boolean; duration?: string | null }) {
  const items = buildItems(nodes);
  if (items.length === 0) {
    return null;
  }
  const title = running ? "思考中" : duration ? `本轮对话耗时 ${duration}` : "思考过程";
  return (
    <details className={styles.processRoot}>
      <summary className={styles.processRootHead}>
        <IconBulb className={styles.processIcon} />
        <span className={running ? styles.processRootLabelSweeping : undefined}>{title}</span>
      </summary>
      <div className={styles.processList}>
        {items.map((item) =>
          item.kind === "TOOL_GROUP" ? (
            <ToolGroupItem key={`group-${item.nodes[0].key}`} nodes={item.nodes} />
          ) : item.presentation.kind === "THINKING" || item.presentation.kind === "REASONING" ? (
            <TextItem key={item.node.key} presentation={item.presentation} />
          ) : item.presentation.kind === "PLAN" ? (
            <PlanItem key={item.node.key} presentation={item.presentation} />
          ) : item.presentation.kind === "STEP" ? (
            <StepItem key={item.node.key} node={item.node} presentation={item.presentation} />
          ) : item.presentation.kind === "APPROVAL" ? (
            <ApprovalItem key={item.node.key} node={item.node} presentation={item.presentation} />
          ) : (
            <EventItem key={item.node.key} presentation={item.presentation} />
          ),
        )}
      </div>
    </details>
  );
}
