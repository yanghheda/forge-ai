import { Alert, Button, Card, DatePicker, Input, Message, Modal, Select, Spin, Tag } from "@arco-design/web-react";
import { IconSearch } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router";
import { useState } from "react";

import { formatRequestError } from "@/lib/api";
import { priorityLabel } from "@/lib/labels";
import { deleteRequirement, listOrganizationRequirements, updateWorkItem, type OrganizationRequirement } from "../api/work-item-api";
import { statusLabel } from "../utils/requirement-detail-display";
import styles from "./requirement-list.module.css";

const statuses = ["DRAFT", "REQUIREMENT_APPROVAL", "READY_FOR_REVIEW", "PRODUCT_REVIEW", "READY_FOR_UX", "UX_IN_PROGRESS", "UX_REVIEW", "READY_FOR_DEV", "IN_DEVELOPMENT", "READY_FOR_QA", "IN_QA", "IN_FIX_VERIFICATION", "IN_ACCEPTANCE", "READY_FOR_RELEASE", "RELEASED", "DONE"];
const priorities = ["LOW", "MEDIUM", "HIGH", "URGENT"];

/* 需求列表是全局需求浏览入口，只列需求，不含 UX/DEV/QA 任务与 Bug。 */
export function RequirementList() {
  const queryClient = useQueryClient();
  const [q, setQ] = useState("");
  const [status, setStatus] = useState<string>();
  const [editing, setEditing] = useState<OrganizationRequirement | null>(null);
  const [priorityDraft, setPriorityDraft] = useState<string>();
  const [dueAtDraft, setDueAtDraft] = useState<string>();
  const [deleting, setDeleting] = useState<OrganizationRequirement | null>(null);
  const requirements = useQuery({
    queryKey: ["requirements", q, status],
    queryFn: () => listOrganizationRequirements({ q, status }),
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ["requirements"] });
  const update = useMutation({
    mutationFn: ({ id, ...changes }: { id: number; priority?: string; dueAt?: string; expectedVersion: number }) => updateWorkItem(id, changes),
    onSuccess: () => {
      Message.success("需求已更新");
      setEditing(null);
      void refresh();
    },
    onError: (error) => {
      Message.error(formatRequestError(error));
      void refresh();
    },
  });
  const remove = useMutation({
    mutationFn: (item: OrganizationRequirement) => deleteRequirement(item.id, item.version),
    onSuccess: () => {
      Message.success("需求已删除");
      setDeleting(null);
      void refresh();
    },
    onError: (error) => {
      Message.error(formatRequestError(error));
      setDeleting(null);
      void refresh();
    },
  });

  function openEdit(item: OrganizationRequirement) {
    setEditing(item);
    setPriorityDraft(item.priority);
    setDueAtDraft(item.dueAt ?? undefined);
  }

  function submitEdit() {
    if (!editing) return;
    const input: { id: number; priority?: string; dueAt?: string; expectedVersion: number } = { id: editing.id, expectedVersion: editing.version };
    if (priorityDraft !== undefined && priorityDraft !== editing.priority) input.priority = priorityDraft;
    if (dueAtDraft && dueAtDraft !== editing.dueAt) input.dueAt = new Date(dueAtDraft).toISOString();
    update.mutate(input);
  }

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
        <span>操作</span>
      </div>
      <div className={styles.list}>
        {requirements.data?.items.map((item) => (
          <div key={item.id} className={styles.row}>
            <Link to={`/requirements/${item.id}`} className={styles.rowLink}>
              <div>
                <small>{item.itemKey}</small>
                <strong>{item.title}</strong>
              </div>
              <Tag color={item.priority === "HIGH" || item.priority === "URGENT" ? "red" : "orange"}>{priorityLabel(item.priority)}</Tag>
              <Tag color={item.status === "DONE" || item.status === "RELEASED" ? "green" : "arcoblue"}>{statusLabel[item.status] ?? item.status}</Tag>
              <time>{new Date(item.updatedAt).toLocaleDateString("zh-CN")}</time>
            </Link>
            <div className={styles.rowActions}>
              <Button type="text" size="small" onClick={() => openEdit(item)}>
                修改
              </Button>
              {item.status === "DRAFT" && (
                <Button type="text" size="small" status="danger" onClick={() => setDeleting(item)}>
                  删除
                </Button>
              )}
            </div>
          </div>
        ))}
      </div>
      {requirements.data && <div className={styles.total}>共 {requirements.data.total} 条需求</div>}
      <Modal visible={editing !== null} title="修改需求" okText="保存" cancelText="取消" confirmLoading={update.isPending} onOk={submitEdit} onCancel={() => setEditing(null)} unmountOnExit>
        {editing && (
          <div className={styles.editFields}>
            <div className={styles.editField}>
              <span>优先级</span>
              <Select aria-label="优先级" value={priorityDraft} onChange={setPriorityDraft} options={priorities.map((value) => ({ value, label: `${priorityLabel(value)}优先级` }))} />
            </div>
            <div className={styles.editField}>
              <span>期望发布时间</span>
              <DatePicker aria-label="期望发布时间" value={dueAtDraft} allowClear={false} placeholder="选择期望发布时间" onChange={(dateString) => setDueAtDraft(dateString)} />
            </div>
          </div>
        )}
      </Modal>
      <Modal
        visible={deleting !== null}
        title="删除需求"
        okText="确认删除"
        cancelText="取消"
        okButtonProps={{ status: "danger" }}
        confirmLoading={remove.isPending}
        onOk={() => {
          if (deleting) remove.mutate(deleting);
        }}
        onCancel={() => setDeleting(null)}
        unmountOnExit
      >
        {deleting && <p>删除后「{deleting.title}」将不再出现在需求列表中，请确认是否继续。</p>}
      </Modal>
    </Card>
  );
}
