"use client";

import { Alert, Button, Card, Input, Space, Spin, Tag, Tabs, Typography } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { getCurrentUser } from "@/features/auth";
import { formatRequestError } from "@/lib/api";
import { guardHintLabel, workflowActionLabel } from "@/lib/labels";
import { createDocument, DocumentEditor, listDocumentVersions, listWorkItemDocuments, publishDocumentVersion, saveDocumentVersion, type ProseMirrorDocument } from "@/features/document";
import { getRequirementDetails, createDevTask, getWorkItem, getWorkItemActivity, transitionRequirement, type WorkflowAction } from "../api/work-item-api";
import { DeliveryGraphPanel } from "./delivery-graph";
import { DevelopmentPanel } from "./development-panel";
import { QaPanel } from "./qa-panel";
import { RequirementMaterials } from "./requirement-materials";
import { UxReviewChecklist } from "./ux-review-checklist";
import { selectEditableVersion, unresolvedUxReviewHints } from "../utils/ux-review";
import styles from "./requirement-detail.module.css";

export { UxReviewChecklist, selectEditableVersion, unresolvedUxReviewHints };
export function RequirementDetail({ organizationId, workItemId, userId }: { organizationId: number; workItemId: number; userId: number }) {
  const qc = useQueryClient();
  const detail = useQuery({
    queryKey: ["work-item", workItemId],
    queryFn: () => getWorkItem(organizationId, workItemId),
  });
  const materials = useQuery({
    queryKey: ["requirement-details", workItemId],
    queryFn: () => getRequirementDetails(workItemId),
  });
  const documents = useQuery({
    queryKey: ["documents", workItemId],
    queryFn: () => listWorkItemDocuments(organizationId, workItemId),
  });
  const activity = useQuery({
    queryKey: ["work-item-activity", workItemId],
    queryFn: () => getWorkItemActivity(organizationId, workItemId),
  });
  const [reason, setReason] = useState("");
  const [checklist, setChecklist] = useState({
    userFlow: false,
    pageList: false,
    keyInteraction: false,
    exceptionState: false,
  });
  const invalidate = () =>
    Promise.all([qc.invalidateQueries({ queryKey: ["work-item", workItemId] }), qc.invalidateQueries({ queryKey: ["requirement-details", workItemId] }), qc.invalidateQueries({ queryKey: ["documents", workItemId] }), qc.invalidateQueries({ queryKey: ["work-item-activity", workItemId] })]);
  const createPrd = useMutation({
    mutationFn: () =>
      createDocument({
        organizationId,
        workItemId,
        type: "PRD",
        title: `${detail.data!.title} PRD`,
      }),
    onSuccess: () => void invalidate(),
  });
  const createUxSpec = useMutation({
    mutationFn: () =>
      createDocument({
        organizationId,
        workItemId,
        type: "UX_SPEC",
        title: `${detail.data!.title} UX Spec`,
      }),
    onSuccess: () => void invalidate(),
  });
  const transition = useMutation({
    mutationFn: (action: WorkflowAction) =>
      transitionRequirement(
        organizationId,
        workItemId,
        action,
        detail.data!.version,
        reason,
        action === "SUBMIT_UX_REVIEW"
          ? Object.entries(checklist)
              .filter(([, checked]) => checked)
              .map(([item]) => item)
          : undefined,
      ),
    onSuccess: () => void invalidate(),
  });
  if (detail.isPending || materials.isPending || documents.isPending) return <Spin tip="正在加载需求…" />;
  if (!detail.data || !materials.data) return <Alert type="error" content="需求不存在或无权访问。" />;
  const prd = documents.data?.find((document) => document.type === "PRD");
  const uxSpec = documents.data?.find((document) => document.type === "UX_SPEC");
  const uxReviewHints = unresolvedUxReviewHints(detail.data.guardHints.SUBMIT_UX_REVIEW, checklist);
  const error = createPrd.error ?? createUxSpec.error ?? transition.error;
  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <div>
          <Typography.Text className={styles.eyebrow}>需求详情</Typography.Text>
          <Typography.Title heading={2} className={styles.title}>
            <span>{detail.data.itemKey}</span>
            {detail.data.title}
          </Typography.Title>
        </div>
        <div className={styles.statusGroup}>
          <Tag color="arcoblue">{detail.data.status}</Tag>
          <Typography.Text type="secondary">版本 {detail.data.version}</Typography.Text>
        </div>
      </header>
      {error && <Alert className={styles.pageAlert} type="error" content={formatRequestError(error)} />}
      <Tabs defaultActiveTab="details" className={styles.tabs}>
        <Tabs.TabPane key="details" title="需求">
          <RequirementMaterials key={materials.data.version} workItemId={workItemId} details={materials.data} onChanged={invalidate} />
        </Tabs.TabPane>
        <Tabs.TabPane key="prd" title="PRD">
          <PrdPanel documentType="PRD" userId={userId} organizationId={organizationId} workItemId={workItemId} document={prd} onChanged={invalidate} create={() => createPrd.mutate()} />
        </Tabs.TabPane>
        <Tabs.TabPane key="ux" title="UX Spec">
          <PrdPanel documentType="UX Spec" userId={userId} organizationId={organizationId} workItemId={workItemId} document={uxSpec} onChanged={invalidate} create={() => createUxSpec.mutate()} />
        </Tabs.TabPane>
        <Tabs.TabPane key="activity" title="动态">
          {activity.data?.map((item) => (
            <p key={`${item.kind}-${item.id}`}>{item.kind === "COMMENT" ? item.body : `${item.action}${item.reason ? `：${item.reason}` : ""}`}</p>
          ))}
        </Tabs.TabPane>
        <Tabs.TabPane key="delivery" title="交付关系图">
          <DeliveryGraphPanel organizationId={organizationId} workItemId={workItemId} />
        </Tabs.TabPane>
      </Tabs>
      {detail.data.availableActions.length > 0 && (
        <Card title="阶段操作" className={styles.actionCard}>
          <Space direction="vertical" style={{ width: "100%" }}>
            {detail.data.availableActions.includes("SUBMIT_UX_REVIEW") && (
              <UxReviewChecklist checklist={checklist} needsPublishedUxSpec={detail.data.guardHints.SUBMIT_UX_REVIEW?.includes("publishedUxSpec") ?? false} onChange={(item, checked) => setChecklist((current) => ({ ...current, [item]: checked }))} />
            )}
            {detail.data.availableActions.map((action) => (
              <div key={action}>
                <Button type={action === "SUBMIT_UX_REVIEW" ? "primary" : "secondary"} loading={transition.isPending} disabled={action === "SUBMIT_UX_REVIEW" && (uxSpec?.status !== "PUBLISHED" || uxReviewHints.length > 0)} onClick={() => transition.mutate(action)}>
                  {workflowActionLabel(action)}
                </Button>
                {(action === "SUBMIT_UX_REVIEW" ? uxReviewHints : detail.data.guardHints[action])?.length ? (
                  <Typography.Text type="secondary">
                    {" "}
                    缺少：
                    {(action === "SUBMIT_UX_REVIEW" ? uxReviewHints : detail.data.guardHints[action])!.map((value) => guardHintLabel(value)).join("、")}
                  </Typography.Text>
                ) : null}
              </div>
            ))}
            {(detail.data.availableActions.includes("REJECT_PRODUCT_REVIEW") || detail.data.availableActions.includes("REJECT_UX_REVIEW") || detail.data.availableActions.includes("SKIP_UX")) && <Input aria-label="动作原因" value={reason} onChange={setReason} placeholder="退回或跳过原因" />}
          </Space>
        </Card>
      )}
      <div className={styles.phasePanels}>
        {detail.data.status === "READY_FOR_DEV" && <DevTaskPanel organizationId={organizationId} requirementId={workItemId} onChanged={invalidate} />}
        {(detail.data.status === "READY_FOR_DEV" || detail.data.status === "IN_DEVELOPMENT") && <DevelopmentPanel organizationId={organizationId} requirementId={workItemId} onChanged={invalidate} />}
        {(detail.data.status === "READY_FOR_QA" || detail.data.status === "IN_QA") && <QaPanel organizationId={organizationId} requirementId={workItemId} onChanged={invalidate} />}
      </div>
    </section>
  );
}

function DevTaskPanel({ organizationId, requirementId, onChanged }: { organizationId: number; requirementId: number; onChanged: () => Promise<unknown> }) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const create = useMutation({
    mutationFn: () => createDevTask({ organizationId, requirementId, title, description }),
    onSuccess: () => {
      setTitle("");
      setDescription("");
      void onChanged();
    },
  });
  return (
    <Card title="研发任务">
      <Space direction="vertical" style={{ width: "100%" }}>
        <Input aria-label="研发任务标题" value={title} onChange={setTitle} placeholder="输入研发任务标题" />
        <Input.TextArea aria-label="研发任务说明" value={description} onChange={setDescription} placeholder="实现范围与约束" />
        <Button type="primary" disabled={!title.trim()} loading={create.isPending} onClick={() => create.mutate()}>
          创建研发任务
        </Button>
        {create.isError && <Alert type="error" content={formatRequestError(create.error)} />}
      </Space>
    </Card>
  );
}

export function RequirementRoute({ workItemId }: { workItemId: number }) {
  const user = useQuery({
    queryKey: ["current-user"],
    queryFn: () => getCurrentUser(),
  });
  const organization = user.data?.organization;
  if (user.isPending) return <Spin />;
  if (!user.data || !organization) return <Alert type="error" content="公司不存在或当前账户无权访问。" />;
  return <RequirementDetail userId={user.data.id} organizationId={organization.id} workItemId={workItemId} />;
}

function PrdPanel({
  documentType,
  userId,
  organizationId,
  document,
  onChanged,
  create,
}: {
  documentType: "PRD" | "UX Spec";
  userId: number;
  organizationId: number;
  workItemId: number;
  document: import("@/features/document").Document | undefined;
  onChanged: () => Promise<unknown>;
  create: () => void;
}) {
  const qc = useQueryClient();
  const versions = useQuery({
    queryKey: ["document-versions", document?.id],
    queryFn: () => listDocumentVersions(organizationId, document!.id),
    enabled: !!document,
  });
  const save = useMutation({
    mutationFn: (content: ProseMirrorDocument) => saveDocumentVersion(organizationId, document!.id, document!.version, content),
    onSuccess: () => {
      void Promise.all([qc.invalidateQueries({ queryKey: ["document-versions", document?.id] }), onChanged()]);
    },
  });
  const publish = useMutation({
    mutationFn: (versionId: number) => publishDocumentVersion(organizationId, document!.id, versionId, document!.version),
    onSuccess: () => void onChanged(),
  });
  if (!document) return <Button onClick={create}>创建关联 {documentType}</Button>;
  if (versions.isPending) return <Spin tip={`正在加载 ${documentType}…`} />;
  if (versions.isError) {
    return <Alert type="error" content={formatRequestError(versions.error)} />;
  }
  const current = selectEditableVersion(versions.data);
  return (
    <div className={styles.documentLayout}>
      <div className={styles.editorPane}>
        <div className={styles.documentHeader}>
          <div>
            <Typography.Title heading={5}>{document.title}</Typography.Title>
            <Typography.Text type="secondary">文档版本 {document.version}</Typography.Text>
          </div>
          <div className={styles.publishGroup}>
            <Tag color={document.status === "PUBLISHED" ? "green" : "orange"}>{document.status}</Tag>
            <Button type="primary" loading={publish.isPending} disabled={!current} onClick={() => current && publish.mutate(current.id)}>
              {current ? `发布 v${current.versionNo}` : "暂无可发布版本"}
            </Button>
          </div>
        </div>
        <div className={styles.editorSurface}>
          <DocumentEditor
            key={`${document.id}-${current?.id ?? "empty"}`}
            userId={userId}
            documentId={document.id}
            baseVersion={document.version}
            serverContent={current?.content ?? { type: "doc", content: [{ type: "paragraph" }] }}
            onSave={(content) => save.mutateAsync(content).then(() => undefined)}
          />
        </div>
        {(save.isError || publish.isError) && <Alert type="error" content={formatRequestError(save.error ?? publish.error)} />}
      </div>
      <aside className={styles.historyPane}>
        <div className={styles.historyHeader}>
          <Typography.Text className={styles.historyTitle}>版本历史</Typography.Text>
          <Typography.Text type="secondary">{versions.data?.length ?? 0} 个版本</Typography.Text>
        </div>
        <div className={styles.historyList}>
          {versions.data?.map((version) => (
            <div className={styles.historyItem} key={version.id}>
              <strong>v{version.versionNo}</strong>
              <code>{version.contentHash.slice(0, 8)}</code>
              {version.id === current?.id && <span>最新</span>}
            </div>
          ))}
          {!versions.isPending && versions.data?.length === 0 && <Typography.Text type="secondary">保存后将在这里生成版本记录。</Typography.Text>}
        </div>
      </aside>
    </div>
  );
}
