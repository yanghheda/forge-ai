"use client";

import { Alert, Button, Card, Drawer, Input, Message, Select, Spin, Tag, Typography } from "@arco-design/web-react";
import { IconCheckCircle, IconClockCircle, IconFile, IconPlus, IconSearch } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";

import { formatRequestError } from "@/lib/api";
import { priorityLabel } from "@/lib/labels";
import { createOrganizationRequirement, getRequirementOverview, listOrganizationRequirements } from "../api/work-item-api";
import styles from "./requirement-dashboard.module.css";

const statuses = ["DRAFT", "PRODUCT_REVIEW", "UX_IN_PROGRESS", "UX_REVIEW", "READY_FOR_DEV", "IN_DEVELOPMENT", "READY_FOR_QA", "IN_QA", "READY_FOR_RELEASE", "RELEASED", "DONE"];

export function RequirementDashboard({ mine = false }: { mine?: boolean }) {
  const queryClient = useQueryClient();
  const [q, setQ] = useState("");
  const [status, setStatus] = useState<string>();
  const [creating, setCreating] = useState(false);
  const [draft, setDraft] = useState({ title: "", description: "", priority: "MEDIUM" });
  const overview = useQuery({ queryKey: ["requirements", "overview"], queryFn: () => getRequirementOverview(), enabled: !mine });
  const requirements = useQuery({
    queryKey: ["requirements", mine ? "mine" : "all", q, status],
    queryFn: () => listOrganizationRequirements({ mine, q, status }),
  });
  const create = useMutation({
    mutationFn: () => createOrganizationRequirement(draft),
    onSuccess: () => {
      Message.success("需求创建成功。");
      setDraft({ title: "", description: "", priority: "MEDIUM" });
      setCreating(false);
      void queryClient.invalidateQueries({ queryKey: ["requirements"] });
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });

  function closeCreateDrawer() {
    setCreating(false);
    setDraft({ title: "", description: "", priority: "MEDIUM" });
  }

  return <section className={styles.page} aria-labelledby="requirement-page-title">
    <header className={styles.header}><div><span className={styles.eyebrow}>{mine ? "MY REQUIREMENTS" : "REQUIREMENT OVERVIEW"}</span><Typography.Title id="requirement-page-title" heading={2}>{mine ? "我的需求" : "当前需求概览"}</Typography.Title><Typography.Paragraph>{mine ? "只查看你在产品、UX、开发或测试环节承担职责的需求。" : "从需求进入，查看公司当前交付工作的整体进展。"}</Typography.Paragraph></div>{!mine && <Button type="primary" icon={<IconPlus />} onClick={() => setCreating((value) => !value)}>新建需求</Button>}</header>

    {!mine && <div className={styles.metrics}>
      <Metric title="全部需求" value={overview.data?.total} icon={<IconFile />} tone="blue" />
      <Metric title="进行中" value={overview.data?.inProgress} icon={<IconClockCircle />} tone="amber" />
      <Metric title="已完成" value={overview.data?.completed} icon={<IconCheckCircle />} tone="green" />
    </div>}

    <Drawer
      className={styles.createDrawer}
      width={520}
      visible={creating}
      title={<div className={styles.drawerTitle}><span>创建需求</span><small>记录问题背景与优先级，创建后可继续完善协作人员和交付材料。</small></div>}
      footer={<div className={styles.drawerFooter}><Button disabled={create.isPending} onClick={closeCreateDrawer}>取消</Button><Button type="primary" disabled={!draft.title.trim()} loading={create.isPending} onClick={() => create.mutate()}>创建需求</Button></div>}
      maskClosable={!create.isPending}
      onCancel={closeCreateDrawer}
    >
      <div className={styles.drawerForm} role="dialog" aria-label="新建需求">
        <label className={styles.field}><span>需求标题 <i>必填</i></span><Input aria-label="需求标题" size="large" value={draft.title} placeholder="一句话说明要解决的问题" maxLength={120} showWordLimit onChange={(title) => setDraft((value) => ({ ...value, title }))} /><small>建议使用清晰的目标或问题描述，便于团队快速识别。</small></label>
        <label className={styles.field}><span>需求描述</span><Input.TextArea aria-label="需求描述" value={draft.description} placeholder="补充业务背景、目标、用户价值或范围边界" maxLength={2000} showWordLimit rows={7} onChange={(description) => setDraft((value) => ({ ...value, description }))} /></label>
        <label className={styles.field}><span>优先级</span><Select aria-label="需求优先级" size="large" value={draft.priority} onChange={(priority) => setDraft((value) => ({ ...value, priority }))} options={["LOW", "MEDIUM", "HIGH", "URGENT"].map((value) => ({ value, label: `${priorityLabel(value)}优先级` }))} /></label>
        <div className={styles.formHint}><strong>创建后的初始状态</strong><span>需求将以草稿状态保存，可在详情页继续补充材料并发起产品评审。</span></div>
      </div>
    </Drawer>

    <Card className={styles.listCard}>
      <div className={styles.filters}><Input prefix={<IconSearch />} allowClear placeholder="搜索需求标题、编号或描述" value={q} onChange={setQ} /><Select allowClear placeholder="全部状态" value={status} onChange={setStatus} options={statuses.map((value) => ({ label: value, value }))} /></div>
      {requirements.isPending && <div className={styles.loading}><Spin tip="正在加载需求…" /></div>}
      {requirements.isError && <Alert type="error" content={formatRequestError(requirements.error)} />}
      {requirements.data?.items.length === 0 && <div className={styles.empty}>没有匹配的需求。</div>}
      <div className={styles.list}>{requirements.data?.items.map((item) => <Link key={item.id} href={`/requirements/${item.id}`} className={styles.row}><div><strong>{item.title}</strong><span>{item.itemKey} · {priorityLabel(item.priority)}优先级</span></div><Tag color={item.status === "DONE" || item.status === "RELEASED" ? "green" : "arcoblue"}>{item.status}</Tag></Link>)}</div>
      {requirements.data && <div className={styles.total}>共 {requirements.data.total} 条需求</div>}
    </Card>
  </section>;
}

function Metric({ title, value, icon, tone }: { title: string; value?: number; icon: React.ReactNode; tone: "blue" | "amber" | "green" }) {
  return <article className={`${styles.metric} ${styles[tone]}`}><div>{icon}</div><span>{title}</span><strong>{value ?? "—"}</strong></article>;
}
