import { Alert, Message, Select, Spin } from "@arco-design/web-react";
import { IconClockCircle, IconExclamationCircle } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router";

import { getCurrentUser } from "@/features/auth";
import { listRequirementMembers } from "@/features/work-item";
import { formatRequestError } from "@/lib/api";
import { priorityLabel } from "@/lib/labels";
import { listBoardItems, listBoardRequirements, moveBoardItem, type BoardItem } from "../api/console-api";
import styles from "./task-board.module.css";

/* 看板五列固定顺序：泳道值到列标题。 */
const lanes = [
  ["TODO", "待处理"],
  ["IN_PROGRESS", "进行中"],
  ["BLOCKED", "阻塞中"],
  ["WAITING_VERIFICATION", "待验证"],
  ["DONE", "已完成"],
] as const;

/* 顶部统计窄条：阻塞与已完成分别使用错误色与成功色突出。 */
const stats = [
  { lane: "TODO", label: "待处理", tone: undefined },
  { lane: "IN_PROGRESS", label: "进行中", tone: undefined },
  { lane: "BLOCKED", label: "阻塞", tone: "error" },
  { lane: "WAITING_VERIFICATION", label: "待验证", tone: undefined },
  { lane: "DONE", label: "已完成", tone: "success" },
] as const;

/* 任务类型标签文案与配色，用于区分设计、开发、测试与缺陷。 */
const taskTypeMeta: Record<string, { label: string; className: string }> = {
  UX_TASK: { label: "设计", className: styles.tagDesign },
  DEV_TASK: { label: "开发", className: styles.tagDev },
  QA_TASK: { label: "测试", className: styles.tagTest },
  BUG: { label: "缺陷", className: styles.tagBug },
};

/* 优先级圆点配色；缺失优先级时回退到中性灰。 */
const priorityMeta: Record<string, string> = {
  URGENT: styles.prioUrgent,
  HIGH: styles.prioHigh,
  MEDIUM: styles.prioMedium,
  LOW: styles.prioLow,
};

/* 角色优先的详情页路径后缀，数组顺序即命中优先级。 */
const roleTargets: Array<[string, string]> = [
  ["PRODUCT", ""],
  ["UX", "/ux"],
  ["DEVELOPER", "/dev"],
  ["QA", "/qa"],
];

/* 角色未命中时按任务类型兜底到对应工作区。 */
const typeTargets: Record<string, string> = {
  UX_TASK: "/ux",
  DEV_TASK: "/dev",
  QA_TASK: "/qa",
  BUG: "/dev",
};

/* 「全部」哨兵值，不会与真实 userId 或需求 id 冲突。 */
const ALL = "ALL";

type AssigneeFilter = number | typeof ALL;

/**
 * 解析卡片标题的跳转地址：先按当前登录用户角色命中，再退回任务类型。
 * 无关联需求时返回 null，界面渲染为纯文本。
 */
export function taskTarget(roles: string[] | undefined, item: BoardItem): string | null {
  if (item.requirementId === null || item.requirementId === undefined) return null;
  const base = `/requirements/${item.requirementId}`;
  const matched = roleTargets.find(([role]) => roles?.includes(role));
  if (matched) return `${base}${matched[1]}`;
  const byType = typeTargets[item.type];
  return byType ? `${base}${byType}` : base;
}

/* 截止日期格式化为 MM-DD。 */
function formatDue(value: string): string {
  const date = new Date(value);
  return `${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

/* 截止日期早于今天时用错误色标记逾期。 */
function isOverdue(value: string): boolean {
  const due = new Date(value);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return due.getTime() < today.getTime();
}

export function TaskBoard() {
  const queryClient = useQueryClient();
  const [assigneeSelection, setAssigneeSelection] = useState<AssigneeFilter | null>(null);
  const [requirementSelection, setRequirementSelection] = useState<number | typeof ALL>(ALL);
  const [prioritySelection, setPrioritySelection] = useState<string>(ALL);

  const user = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
  const board = useQuery({ queryKey: ["task-board"], queryFn: () => listBoardItems() });
  const members = useQuery({ queryKey: ["requirement-members"], queryFn: () => listRequirementMembers() });

  /* 首次渲染默认选中当前登录用户，用户手动切换后以手动选择为准。 */
  const assignee: AssigneeFilter = assigneeSelection ?? user.data?.id ?? ALL;
  const requirements = useQuery({
    queryKey: ["task-board-requirements", assignee],
    queryFn: () => listBoardRequirements(assignee === ALL ? undefined : assignee),
    enabled: !!user.data,
  });

  const move = useMutation({
    mutationFn: ({ id, lane, position }: { id: number; lane: string; position: number }) => moveBoardItem(id, lane, position),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["task-board"] }),
    onError: (error) => Message.error(formatRequestError(error)),
  });

  /* 当前用户未就绪时先展示加载态，避免出现错误的默认执行人。 */
  if (user.isPending) return <Spin tip="正在加载看板…" />;

  const memberByValue = new Map((members.data ?? []).map((member) => [String(member.userId), member]));
  const roles = user.data?.organization.roles;
  const items = (board.data ?? []).filter(
    (item) =>
      (assignee === ALL || item.assigneeUserId === assignee) &&
      (requirementSelection === ALL || item.requirementId === requirementSelection) &&
      (prioritySelection === ALL || item.priority === prioritySelection),
  );
  const countIn = (lane: string) => items.filter((item) => item.lane === lane).length;

  return (
    <section className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1>任务看板</h1>
          <p>公司全部需求的交付任务与阻塞状态</p>
        </div>
        <Link className={styles.enterLink} to="/requirements">
          进入需求创建任务
        </Link>
      </header>

      <div className={styles.stats} data-testid="board-stats">
        {stats.map((stat) => (
          <div className={styles.stat} key={stat.lane}>
            <span className={styles.statLabel}>{stat.label}</span>
            <b
              className={`${styles.statNum}${stat.tone === "error" ? ` ${styles.statError}` : stat.tone === "success" ? ` ${styles.statSuccess}` : ""}`}
              data-testid={`stat-${stat.lane}`}
            >
              {countIn(stat.lane)}
            </b>
          </div>
        ))}
      </div>

      <div className={styles.toolbar}>
        <Select
          className={styles.select}
          aria-label="执行人"
          showSearch
          value={assignee}
          onChange={(value) => {
            setAssigneeSelection(value as AssigneeFilter);
            /* 执行人变化后需求选项联动刷新，同步把需求筛选重置为全部。 */
            setRequirementSelection(ALL);
          }}
          filterOption={(input, option) => {
            const keyword = input.trim().toLowerCase();
            if (!keyword) return true;
            const props = option.props as { value?: unknown; children?: unknown };
            const member = memberByValue.get(String(props.value));
            if (!member) return String(props.children ?? "").toLowerCase().includes(keyword);
            return member.displayName.toLowerCase().includes(keyword) || member.email.toLowerCase().includes(keyword);
          }}
          options={[{ value: ALL, label: "全部" }, ...(members.data ?? []).map((member) => ({ value: member.userId, label: member.displayName }))]}
        />
        <Select
          className={styles.select}
          aria-label="需求"
          showSearch
          value={requirementSelection}
          onChange={(value) => setRequirementSelection(value as number | typeof ALL)}
          options={[{ value: ALL, label: "全部" }, ...(requirements.data ?? []).map((option) => ({ value: option.id, label: `${option.itemKey} ${option.title}` }))]}
        />
        <Select
          className={styles.select}
          aria-label="优先级"
          value={prioritySelection}
          onChange={(value) => setPrioritySelection(String(value))}
          options={[{ value: ALL, label: "全部" }, ...(["URGENT", "HIGH", "MEDIUM", "LOW"] as const).map((value) => ({ value, label: priorityLabel(value) }))]}
        />
      </div>
      {requirements.isError && <Alert type="error" content={formatRequestError(requirements.error)} />}

      {board.isPending && <Spin tip="正在加载看板…" />}
      {board.isError && <Alert type="error" content={formatRequestError(board.error)} />}

      <div className={styles.kanban}>
        {lanes.map(([lane, title]) => {
          const columnItems = items.filter((item) => item.lane === lane);
          const columnClass = `${styles.column}${lane === "BLOCKED" ? ` ${styles.columnBlocked}` : lane === "DONE" ? ` ${styles.columnDone}` : ""}`;
          return (
            <div
              className={columnClass}
              key={lane}
              data-testid={`column-${lane}`}
              onDragOver={(event) => event.preventDefault()}
              onDrop={(event) => {
                const id = Number(event.dataTransfer.getData("text/work-item-id"));
                const dragged = board.data?.find((item) => item.id === id);
                if (dragged && dragged.lane === lane) move.mutate({ id, lane, position: columnItems.length * 1000 + 1000 });
                else Message.info("请进入任务工作区执行状态操作。");
              }}
            >
              <header className={styles.columnHead}>
                <span className={styles.columnName}>{title}</span>
                <span className={styles.columnCount} data-testid={`count-${lane}`}>
                  {columnItems.length}
                </span>
              </header>
              <div className={styles.columnList}>
                {columnItems.map((item) => {
                  const target = taskTarget(roles, item);
                  const typeMeta = taskTypeMeta[item.type];
                  return (
                    <article
                      className={styles.card}
                      key={item.id}
                      draggable
                      data-testid={`card-${item.id}`}
                      onDragStart={(event) => event.dataTransfer.setData("text/work-item-id", String(item.id))}
                    >
                      <div className={styles.cardTop}>
                        <code className={styles.cardKey}>{item.itemKey}</code>
                        <span className={`${styles.priority} ${priorityMeta[item.priority] ?? styles.prioLow}`} title={priorityLabel(item.priority)} />
                      </div>
                      <h3 className={styles.cardTitle}>{target ? <Link to={target}>{item.title}</Link> : item.title}</h3>
                      <div className={styles.cardTags}>
                        <span className={`${styles.tag} ${typeMeta ? typeMeta.className : styles.tagDev}`}>{typeMeta ? typeMeta.label : item.type}</span>
                      </div>
                      <div className={styles.cardFoot}>
                        {item.assigneeName && <span className={styles.assignee}>{item.assigneeName.slice(0, 1)}</span>}
                        {item.dueAt && (
                          <time className={isOverdue(item.dueAt) ? styles.dueOver : styles.due}>
                            <IconClockCircle />
                            {formatDue(item.dueAt)}
                          </time>
                        )}
                      </div>
                      {item.blockedReason && (
                        <p className={styles.blocked}>
                          <IconExclamationCircle />
                          {item.blockedReason}
                        </p>
                      )}
                    </article>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}