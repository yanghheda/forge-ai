import { Alert, Spin, Tag } from "@arco-design/web-react";
import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router";

import { formatRequestError } from "@/lib/api";
import { getBug } from "../api/work-item-api";
import { bugStatusLabel, severityLabel } from "../utils/requirement-analytics";
import styles from "./defect-detail.module.css";

/* 缺陷详情页为只读视图：状态流转仍在需求 QA / 开发工作区完成，这里不提供任何操作入口。 */
export function DefectDetail() {
  const { id } = useParams();
  const bugId = Number(id);
  const bug = useQuery({ queryKey: ["bug", bugId], queryFn: () => getBug(bugId), enabled: Number.isFinite(bugId) });

  if (bug.isPending) return <Spin tip="正在加载缺陷…" />;
  if (bug.isError) return <Alert type="error" content={formatRequestError(bug.error)} />;
  if (!bug.data) return null;
  const item = bug.data;
  const steps = item.reproductionSteps ?? [];
  const evidence = item.fixEvidence ?? [];
  const fixNote = item.fixNote?.trim();

  return (
    <section className={styles.page} aria-labelledby="defect-detail-title">
      <Link to="/defects" className={styles.back}>
        ← 返回缺陷列表
      </Link>

      <header className={styles.head}>
        <div className={styles.titleLine}>
          <code>{item.itemKey}</code>
          <h1 id="defect-detail-title">{item.title}</h1>
          <Tag color={item.status === "DONE" ? "green" : item.status === "BLOCKED" ? "red" : "arcoblue"}>{bugStatusLabel[item.status] ?? item.status}</Tag>
          <Tag color={item.severity === "BLOCKER" || item.severity === "CRITICAL" ? "red" : item.severity === "MAJOR" ? "orange" : "gray"}>{severityLabel[item.severity] ?? item.severity}</Tag>
        </div>
      </header>

      <dl className={styles.meta}>
        <div className={styles.metaItem}>
          <dt className={styles.metaLabel}>状态</dt>
          <dd className={styles.metaValue}>{bugStatusLabel[item.status] ?? item.status}</dd>
        </div>
        <div className={styles.metaItem}>
          <dt className={styles.metaLabel}>严重级别</dt>
          <dd className={styles.metaValue}>{severityLabel[item.severity] ?? item.severity}</dd>
        </div>
        <div className={styles.metaItem}>
          <dt className={styles.metaLabel}>修复人</dt>
          <dd className={styles.metaValue}>{item.assigneeName ?? "未指派"}</dd>
        </div>
        <div className={styles.metaItem}>
          <dt className={styles.metaLabel}>创建人</dt>
          <dd className={styles.metaValue}>{item.reporterName ?? "—"}</dd>
        </div>
        <div className={styles.metaItem}>
          <dt className={styles.metaLabel}>关联需求</dt>
          <dd className={styles.metaValue}>
            <Link to={`/requirements/${item.requirementId}`}>REQ-{item.requirementId}</Link>
          </dd>
        </div>
        <div className={styles.metaItem}>
          <dt className={styles.metaLabel}>关联开发任务</dt>
          <dd className={styles.metaValue}>{item.devTaskKey ?? "无"}</dd>
        </div>
        <div className={styles.metaItem}>
          <dt className={styles.metaLabel}>创建时间</dt>
          <dd className={styles.metaValue}>{new Date(item.createdAt).toLocaleString("zh-CN")}</dd>
        </div>
        <div className={styles.metaItem}>
          <dt className={styles.metaLabel}>更新时间</dt>
          <dd className={styles.metaValue}>{new Date(item.updatedAt).toLocaleString("zh-CN")}</dd>
        </div>
      </dl>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>缺陷内容</h2>
        <div className={styles.field}>
          <h3 className={styles.fieldLabel}>复现步骤</h3>
          {steps.length > 0 ? (
            <ol className={styles.steps}>
              {steps.map((step, index) => (
                <li key={index}>{step}</li>
              ))}
            </ol>
          ) : (
            <p className={styles.placeholder}>暂无</p>
          )}
        </div>
        <div className={styles.field}>
          <h3 className={styles.fieldLabel}>预期结果</h3>
          <p className={styles.prose}>{item.expectedResult}</p>
        </div>
        <div className={styles.field}>
          <h3 className={styles.fieldLabel}>实际结果</h3>
          <p className={styles.prose}>{item.actualResult}</p>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>修复信息</h2>
        <div className={styles.field}>
          <h3 className={styles.fieldLabel}>修复说明</h3>
          {fixNote ? <p className={styles.prose}>{fixNote}</p> : <p className={styles.placeholder}>暂无</p>}
        </div>
        <div className={styles.field}>
          <h3 className={styles.fieldLabel}>修复证据</h3>
          {evidence.length > 0 ? (
            <ul className={styles.evidence}>
              {evidence.map((entry, index) => (
                <li key={index}>{entry}</li>
              ))}
            </ul>
          ) : (
            <p className={styles.placeholder}>暂无</p>
          )}
        </div>
      </section>
    </section>
  );
}
