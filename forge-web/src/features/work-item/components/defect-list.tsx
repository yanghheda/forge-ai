import { Alert, Button, Radio, Select, Spin, Tag } from "@arco-design/web-react";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router";

import { getCurrentUser } from "@/features/auth";
import { formatRequestError } from "@/lib/api";
import { useAgentPanelStore } from "@/stores/use-agent-panel-store";
import { listCompanyBugs, listRequirementMembers } from "../api/work-item-api";
import { bugStatusLabel, severityLabel } from "../utils/requirement-analytics";
import styles from "./defect-list.module.css";

/* 缺陷状态固定顺序：同时用于顶部统计窄条与状态筛选选项。 */
const defectStatusOrder = ["TODO", "IN_PROGRESS", "BLOCKED", "WAITING_VERIFICATION", "DONE"] as const;

/* 「全部」哨兵值，不会与真实 userId 冲突。 */
const ALL = "ALL";

/* 缺陷列表是公司范围缺陷的统一入口，只读展示，状态流转仍在需求工作区完成。 */
export function DefectList() {
  const setContext = useAgentPanelStore((state) => state.setContext);
  const setAgentOpen = useAgentPanelStore((state) => state.setOpen);
  const [scope, setScope] = useState<"ALL" | "MINE">("ALL");
  const [status, setStatus] = useState<string>();
  const [assignee, setAssignee] = useState<number | typeof ALL>(ALL);

  const user = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
  const bugs = useQuery({ queryKey: ["company-bugs"], queryFn: () => listCompanyBugs() });
  const members = useQuery({ queryKey: ["requirement-members"], queryFn: () => listRequirementMembers() });

  /* 当前用户未就绪时先展示加载态，避免「我的」筛选与未知用户比较。 */
  if (user.isPending) return <Spin tip="正在加载缺陷…" />;

  const memberByValue = new Map((members.data ?? []).map((member) => [String(member.userId), member]));
  const filtered = (bugs.data ?? []).filter((bug) => (scope === "ALL" || bug.assigneeUserId === user.data?.id) && (status === undefined || bug.status === status) && (assignee === ALL || bug.assigneeUserId === assignee));
  const countIn = (value: string) => filtered.filter((bug) => bug.status === value).length;

  return (
    <section className={styles.page} aria-labelledby="defect-list-title">
      <header className={styles.head}>
        <h1 id="defect-list-title">缺陷列表</h1>
        <p>公司全部缺陷；开发修复与测试验证的统一入口</p>
      </header>

      <div className={styles.stats} data-testid="defect-stats">
        {defectStatusOrder.map((value) => (
          <div className={styles.stat} key={value}>
            <span className={styles.statLabel}>{bugStatusLabel[value]}</span>
            <b className={`${styles.statNum}${value === "BLOCKED" ? ` ${styles.statError}` : value === "DONE" ? ` ${styles.statSuccess}` : ""}`} data-testid={`defect-stat-${value}`}>
              {countIn(value)}
            </b>
          </div>
        ))}
      </div>

      <div className={styles.toolbar}>
        <Radio.Group type="button" aria-label="类型" value={scope} onChange={(value) => setScope(value as "ALL" | "MINE")}>
          <Radio value={ALL}>全部</Radio>
          <Radio value="MINE">我的</Radio>
        </Radio.Group>
        <Select className={styles.select} aria-label="状态" allowClear placeholder="全部状态" value={status} onChange={(value) => setStatus(value as string | undefined)} options={defectStatusOrder.map((value) => ({ value, label: bugStatusLabel[value] }))} />
        <Select
          className={styles.select}
          aria-label="修复人"
          showSearch
          value={assignee}
          onChange={(value) => setAssignee(value as number | typeof ALL)}
          filterOption={(input, option) => {
            const keyword = input.trim().toLowerCase();
            if (!keyword) return true;
            const props = option.props as { value?: unknown; children?: unknown };
            const member = memberByValue.get(String(props.value));
            if (!member)
              return String(props.children ?? "")
                .toLowerCase()
                .includes(keyword);
            return member.displayName.toLowerCase().includes(keyword) || member.email.toLowerCase().includes(keyword);
          }}
          options={[{ value: ALL, label: "全部" }, ...(members.data ?? []).map((member) => ({ value: member.userId, label: member.displayName }))]}
        />
      </div>

      {bugs.isPending && <Spin tip="正在加载缺陷…" />}
      {bugs.isError && <Alert type="error" content={formatRequestError(bugs.error)} />}

      <div className={styles.tableHead}>
        <span>编号 / 缺陷标题</span>
        <span>严重级别</span>
        <span>状态</span>
        <span>修复人</span>
        <span>创建人</span>
        <span>创建时间</span>
        <span>操作</span>
      </div>
      <div className={styles.list}>
        {bugs.data && filtered.length === 0 && <div className={styles.empty}>没有匹配的缺陷。</div>}
        {filtered.map((bug) => (
          <div key={bug.id} className={`${styles.row}${bug.status === "BLOCKED" ? ` ${styles.rowBlocked}` : ""}`}>
            <Link to={`/defects/${bug.id}`} className={styles.rowLink} data-testid={`defect-row-${bug.id}`}>
              <div className={styles.titleCell}>
                <code>{bug.itemKey}</code>
                <strong>{bug.title}</strong>
              </div>
              <Tag className={styles.tag} color={bug.severity === "BLOCKER" || bug.severity === "CRITICAL" ? "red" : bug.severity === "MAJOR" ? "orange" : "gray"}>
                {severityLabel[bug.severity] ?? bug.severity}
              </Tag>
              <Tag className={styles.tag} color={bug.status === "DONE" ? "green" : bug.status === "BLOCKED" ? "red" : "arcoblue"}>
                {bugStatusLabel[bug.status] ?? bug.status}
              </Tag>
              <span className={styles.assignee}>{bug.assigneeName ?? "—"}</span>
              <span className={styles.reporter}>{bug.reporterName ?? "—"}</span>
              <time className={styles.createdAt}>{new Date(bug.createdAt).toLocaleDateString("zh-CN")}</time>
            </Link>
            <div className={styles.rowActions}>
              <Button
                type="text"
                size="small"
                onClick={() => {
                  setContext({ kind: "defect", id: bug.id, label: `${bug.itemKey} ${bug.title}` });
                  setAgentOpen(true);
                }}
              >
                交给 Agent
              </Button>
            </div>
          </div>
        ))}
      </div>
      {bugs.data && <div className={styles.total}>共 {filtered.length} 条缺陷</div>}
    </section>
  );
}
