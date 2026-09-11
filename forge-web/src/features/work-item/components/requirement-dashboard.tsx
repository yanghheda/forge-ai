"use client";

import { Alert, Button, Card, Drawer, Form, Input, Message, Select, Spin, Tag, Typography } from "@arco-design/web-react";
import { IconCheckCircle, IconClockCircle, IconFile, IconPlus, IconRobot, IconSearch } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";

import { formatRequestError } from "@/lib/api";
import { priorityLabel } from "@/lib/labels";
import { getDashboardOverview } from "@/features/console";
import { createOrganizationRequirement, getRequirementOverview, listOrganizationRequirements } from "../api/work-item-api";
import styles from "./requirement-dashboard.module.css";

const statuses = ["DRAFT", "PRODUCT_REVIEW", "UX_IN_PROGRESS", "UX_REVIEW", "READY_FOR_DEV", "IN_DEVELOPMENT", "READY_FOR_QA", "IN_QA", "READY_FOR_RELEASE", "RELEASED", "DONE"];
interface CreateRequirementValues {
  title: string;
  description: string;
  priority: string;
}
const emptyRequirement: CreateRequirementValues = {
  title: "",
  description: "",
  priority: "MEDIUM",
};

export function RequirementDashboard({ mine = false }: { mine?: boolean }) {
  const queryClient = useQueryClient();
  const [q, setQ] = useState("");
  const [status, setStatus] = useState<string>();
  const [creating, setCreating] = useState(false);
  const [createForm] = Form.useForm<CreateRequirementValues>();
  const createTitle = Form.useWatch("title", createForm);
  const overview = useQuery({
    queryKey: ["requirements", "overview"],
    queryFn: () => getRequirementOverview(),
    enabled: !mine,
  });
  const dashboard = useQuery({
    queryKey: ["dashboard", "overview"],
    queryFn: () => getDashboardOverview(),
    enabled: !mine,
  });
  const requirements = useQuery({
    queryKey: ["requirements", mine ? "mine" : "all", q, status],
    queryFn: () => listOrganizationRequirements({ mine, q, status }),
  });
  const create = useMutation({
    mutationFn: (values: CreateRequirementValues) => createOrganizationRequirement(values),
    onSuccess: () => {
      Message.success("需求创建成功。");
      createForm.resetFields();
      setCreating(false);
      void queryClient.invalidateQueries({ queryKey: ["requirements"] });
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });

  function closeCreateDrawer() {
    setCreating(false);
    createForm.resetFields();
  }

  return (
    <section className={styles.page} aria-labelledby="requirement-page-title">
      <header className={styles.header}>
        <div>
          <Typography.Title id="requirement-page-title" heading={2}>
            {mine ? "我的需求" : "需求概览"}
          </Typography.Title>
          <Typography.Paragraph>{mine ? "我创建、我负责或等待我评审的需求" : "公司需求交付全景与 Agent 运行动态"}</Typography.Paragraph>
        </div>
        <div className={styles.actions}>
          {!mine && <Button>导入 PRD</Button>}
          <Button type="primary" icon={<IconPlus />} onClick={() => setCreating((value) => !value)}>
            新建需求
          </Button>
        </div>
      </header>

      {!mine && (
        <div className={styles.metrics}>
          <Metric title="进行中需求" value={overview.data?.inProgress} note="+3 较上周" icon={<IconClockCircle />} tone="blue" />
          <Metric title="待我处理" value={dashboard.data?.personalTodos} note="当前分配给我的未完成事项" icon={<IconFile />} tone="red" />
          <Metric title="本周已交付" value={dashboard.data?.weeklyDeliveries ?? overview.data?.completed} note="最近 7 天完成或发布" icon={<IconCheckCircle />} tone="green" />
          <Metric title="活跃 Agent" value={dashboard.data?.activeAgents} note="最近 7 天产生运行记录" icon={<IconRobot />} tone="blue" />
        </div>
      )}

      <Drawer
        className={styles.createDrawer}
        width={520}
        visible={creating}
        title="创建需求"
        footer={
          <div className={styles.drawerFooter}>
            <Button disabled={create.isPending} onClick={closeCreateDrawer}>
              取消
            </Button>
            <Button type="primary" disabled={!createTitle?.trim()} loading={create.isPending} onClick={() => createForm.submit()}>
              创建需求
            </Button>
          </div>
        }
        maskClosable={!create.isPending}
        onCancel={closeCreateDrawer}
      >
        <p className={styles.drawerDescription}>记录问题背景与优先级，创建后可继续完善协作人员和交付材料。</p>
        <Form form={createForm} className={styles.drawerForm} layout="vertical" aria-label="新建需求表单" initialValues={emptyRequirement} requiredSymbol={{ position: "start" }} onSubmit={(values) => create.mutate(values)}>
          <Form.Item
            field="title"
            label="需求标题"
            required
            rules={[
              { required: true, message: "请输入需求标题" },
              { maxLength: 120, message: "需求标题不能超过 120 个字符" },
            ]}
            extra="建议使用清晰的目标或问题描述，便于团队快速识别。"
          >
            <Input aria-label="需求标题" size="large" placeholder="一句话说明要解决的问题" maxLength={120} showWordLimit />
          </Form.Item>
          <Form.Item field="description" label="需求描述" rules={[{ maxLength: 2000, message: "需求描述不能超过 2000 个字符" }]}>
            <Input.TextArea aria-label="需求描述" placeholder="补充业务背景、目标、用户价值或范围边界" maxLength={2000} showWordLimit rows={7} />
          </Form.Item>
          <Form.Item field="priority" label="优先级" required rules={[{ required: true, message: "请选择优先级" }]}>
            <Select
              aria-label="需求优先级"
              size="large"
              options={["LOW", "MEDIUM", "HIGH", "URGENT"].map((value) => ({
                value,
                label: `${priorityLabel(value)}优先级`,
              }))}
            />
          </Form.Item>
          <div className={styles.formHint}>
            <strong>创建后的初始状态</strong>
            <span>需求将以草稿状态保存，可在详情页继续补充材料并发起产品评审。</span>
          </div>
        </Form>
      </Drawer>

      <div className={styles.mainGrid}>
        <Card className={styles.listCard} title="需求列表">
          <div className={styles.filters}>
            <Input prefix={<IconSearch />} allowClear placeholder="搜索需求标题、编号或描述" value={q} onChange={setQ} />
            <Select allowClear placeholder="全部状态" value={status} onChange={setStatus} options={statuses.map((value) => ({ label: value, value }))} />
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
                <Tag color={item.status === "DONE" || item.status === "RELEASED" ? "green" : "arcoblue"}>{item.status}</Tag>
                <time>{new Date(item.updatedAt).toLocaleDateString("zh-CN")}</time>
              </Link>
            ))}
          </div>
          {requirements.data && <div className={styles.total}>共 {requirements.data.total} 条需求</div>}
        </Card>
        {!mine && (
          <aside className={styles.side}>
            <Card title="近 7 日交付趋势">
              <div className={styles.trend}>
                {dashboard.data?.deliveryTrend.map((point) => (
                  <div key={point.date}>
                    <i style={{ height: `${Math.max(6, point.value * 14)}px` }} />
                    <small>{point.date.slice(5)}</small>
                  </div>
                ))}
              </div>
            </Card>
            <Card title="交付阶段分布">
              <div className={styles.stageBar}>
                <i />
                <i />
                <i />
                <i />
                <i />
              </div>
              <p className={styles.legend}>
                {Object.entries(dashboard.data?.stageDistribution ?? {})
                  .map(([key, value]) => `${key} ${value}`)
                  .join(" · ") || "暂无阶段数据"}
              </p>
            </Card>
          </aside>
        )}
      </div>
    </section>
  );
}

function Metric({ title, value, note, icon, tone }: { title: string; value?: number; note: string; icon: React.ReactNode; tone: "blue" | "red" | "green" }) {
  return (
    <article className={`${styles.metric} ${styles[tone]}`}>
      <div className={styles.metricText}>
        <span>{title}</span>
        <strong>{value ?? "—"}</strong>
        <small>{note}</small>
      </div>
      <div className={styles.metricIcon}>{icon}</div>
    </article>
  );
}
