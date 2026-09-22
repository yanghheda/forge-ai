"use client";

import { Alert, Card, Input, Select, Spin, Tag } from "@arco-design/web-react";
import { IconSearch } from "@arco-design/web-react/icon";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";

import { formatRequestError } from "@/lib/api";
import { priorityLabel } from "@/lib/labels";
import { listOrganizationRequirements } from "../api/work-item-api";
import { statusLabel } from "../utils/requirement-detail-display";
import styles from "./requirement-list.module.css";

const statuses = ["DRAFT", "REQUIREMENT_APPROVAL", "READY_FOR_REVIEW", "PRODUCT_REVIEW", "READY_FOR_UX", "UX_IN_PROGRESS", "UX_REVIEW", "READY_FOR_DEV", "IN_DEVELOPMENT", "READY_FOR_QA", "IN_QA", "IN_FIX_VERIFICATION", "IN_ACCEPTANCE", "READY_FOR_RELEASE", "RELEASED", "DONE"];

/* 需求列表是「我的需求」唯一的浏览入口；全局仪表盘不再承担列表职责。 */
export function RequirementList({ mine = false }: { mine?: boolean }) {
  const [q, setQ] = useState("");
  const [status, setStatus] = useState<string>();
  const requirements = useQuery({
    queryKey: ["requirements", mine ? "mine" : "all", q, status],
    queryFn: () => listOrganizationRequirements({ mine, q, status }),
  });

  return (
    <Card className={styles.listCard} title="需求列表">
      <div className={styles.filters}>
        <Input prefix={<IconSearch />} allowClear placeholder="搜索需求标题、编号或描述" value={q} onChange={setQ} />
        <Select allowClear placeholder="全部状态" value={status} onChange={setStatus} options={statuses.map((value) => ({ label: statusLabel[value] ?? value, value }))} />
      </div>
      {requirements.isPending && (
        <div className={styles.loading}>
          <Spin tip="正在加载需求…" />
        </div>
      )}
      {requirements.isError && <Alert type="error" content={formatRequestError(requirements.error)} />}
      {requirements.data?.items.length === 0 && <div className={styles.empty}>没有匹配的需求。</div>}
      <div className={styles.tableHead}>
        <span>编号 / 需求标题</span>
        <span>优先级</span>
        <span>状态</span>
        <span>更新时间</span>
      </div>
      <div className={styles.list}>
        {requirements.data?.items.map((item) => (
          <Link key={item.id} href={`/requirements/${item.id}`} className={styles.row}>
            <div>
              <small>{item.itemKey}</small>
              <strong>{item.title}</strong>
            </div>
            <Tag color={item.priority === "HIGH" || item.priority === "URGENT" ? "red" : "orange"}>{priorityLabel(item.priority)}</Tag>
            <Tag color={item.status === "DONE" || item.status === "RELEASED" ? "green" : "arcoblue"}>{statusLabel[item.status] ?? item.status}</Tag>
            <time>{new Date(item.updatedAt).toLocaleDateString("zh-CN")}</time>
          </Link>
        ))}
      </div>
      {requirements.data && <div className={styles.total}>共 {requirements.data.total} 条需求</div>}
    </Card>
  );
}
