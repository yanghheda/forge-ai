"use client";

import {
  Alert,
  Button,
  Card,
  Input,
  Space,
  Spin,
  Tag,
  Tabs,
  Typography,
} from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { getCurrentUser } from "@/features/auth";
import { listProjects } from "@/features/project";
import { ApiError } from "@/lib/api/api-error";
import {
  createDocument,
  DocumentEditor,
  listDocumentVersions,
  listWorkItemDocuments,
  publishDocumentVersion,
  saveDocumentVersion,
  type ProseMirrorDocument,
  type DocumentVersion,
} from "@/features/document";
import {
  getRequirementDetails,
  createDevTask,
  getWorkItem,
  getWorkItemActivity,
  saveRequirementDetails,
  transitionRequirement,
  type WorkflowAction,
} from "../api/work-item-api";
import { DeliveryGraphPanel } from "./delivery-graph";
import { DevelopmentPanel } from "./development-panel";
import { QaPanel } from "./qa-panel";
import styles from "./requirement-detail.module.css";

const missingLabel: Record<string, string> = {
  goal: "业务目标",
  inScope: "范围",
  acceptanceCriteria: "验收标准",
  publishedPrd: "已发布 PRD",
  publishedUxSpec: "已发布 UX Spec",
  userFlow: "用户流",
  pageList: "页面清单",
  keyInteraction: "关键交互",
  exceptionState: "异常状态",
  reason: "退回原因",
  devTask: "Dev Task",
  devTaskIncomplete: "未完成的 Dev Task",
  repository: "可用 GitLab 仓库",
  mergeRequest: "Dev Task 关联 MR",
  pipeline: "MR Pipeline",
  pipelineRunning: "运行完成的 Pipeline",
  pipelineFailed: "成功的 Pipeline",
  pipelineHeadMismatch: "MR 当前 head 的成功 Pipeline",
  completedTestRun: "已完成 Test Run",
  testNotRun: "全部用例执行完成",
  testFailed: "无失败用例",
  testBlocked: "无阻塞用例",
  mandatoryTestSkipped: "P0/P1 用例全部 PASS",
};

const uxChecklistItems = [
  "userFlow",
  "pageList",
  "keyInteraction",
  "exceptionState",
] as const;
type UxChecklist = Record<(typeof uxChecklistItems)[number], boolean>;

export function UxReviewChecklist({
  checklist,
  needsPublishedUxSpec,
  onChange,
}: {
  checklist: UxChecklist;
  needsPublishedUxSpec: boolean;
  onChange: (item: keyof UxChecklist, checked: boolean) => void;
}) {
  return (
    <>
      {needsPublishedUxSpec && (
        <Alert
          type="warning"
          content="请先在 UX Spec 页签创建文档、保存版本并发布，再确认以下交付检查项。"
        />
      )}
      <Space direction="vertical">
        {uxChecklistItems.map((item) => (
          <label key={item}>
            <input
              type="checkbox"
              checked={checklist[item]}
              onChange={(event) => onChange(item, event.target.checked)}
            />{" "}
            {missingLabel[item] ?? item}
          </label>
        ))}
      </Space>
    </>
  );
}

export function selectEditableVersion(
  versions: DocumentVersion[] | undefined,
  currentVersionId: number | null,
) {
  return versions?.find((version) => version.id === currentVersionId) ?? versions?.[0];
}

export function unresolvedUxReviewHints(hints: string[] | undefined, checklist: UxChecklist) {
  return (hints ?? []).filter(
    (hint) => !uxChecklistItems.includes(hint as keyof UxChecklist) || !checklist[hint as keyof UxChecklist],
  );
}
export function RequirementDetail({
  workspaceId,
  projectId,
  workItemId,
  userId,
}: {
  workspaceId: number;
  projectId: number;
  workItemId: number;
  userId: number;
}) {
  const qc = useQueryClient();
  const detail = useQuery({
    queryKey: ["work-item", workItemId],
    queryFn: () => getWorkItem(workspaceId, projectId, workItemId),
  });
  const materials = useQuery({
    queryKey: ["requirement-details", workItemId],
    queryFn: () => getRequirementDetails(workspaceId, projectId, workItemId),
  });
  const documents = useQuery({
    queryKey: ["documents", workItemId],
    queryFn: () => listWorkItemDocuments(workspaceId, projectId, workItemId),
  });
  const activity = useQuery({
    queryKey: ["work-item-activity", workItemId],
    queryFn: () => getWorkItemActivity(workspaceId, projectId, workItemId),
  });
  const [reason, setReason] = useState("");
  const [checklist, setChecklist] = useState({
    userFlow: false,
    pageList: false,
    keyInteraction: false,
    exceptionState: false,
  });
  const invalidate = () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: ["work-item", workItemId] }),
      qc.invalidateQueries({ queryKey: ["requirement-details", workItemId] }),
      qc.invalidateQueries({ queryKey: ["documents", workItemId] }),
      qc.invalidateQueries({ queryKey: ["work-item-activity", workItemId] }),
    ]);
  const createPrd = useMutation({
    mutationFn: () =>
      createDocument({
        workspaceId,
        projectId,
        workItemId,
        type: "PRD",
        title: `${detail.data!.title} PRD`,
      }),
    onSuccess: () => void invalidate(),
  });
  const createUxSpec = useMutation({
    mutationFn: () =>
      createDocument({
        workspaceId,
        projectId,
        workItemId,
        type: "UX_SPEC",
        title: `${detail.data!.title} UX Spec`,
      }),
    onSuccess: () => void invalidate(),
  });
  const transition = useMutation({
    mutationFn: (action: WorkflowAction) =>
      transitionRequirement(
        workspaceId,
        projectId,
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
  if (detail.isPending || materials.isPending || documents.isPending)
    return <Spin tip="加载 Requirement…" />;
  if (!detail.data || !materials.data)
    return <Alert type="error" content="Requirement 不存在或无权访问。" />;
  const prd = documents.data?.find((document) => document.type === "PRD");
  const uxSpec = documents.data?.find((document) => document.type === "UX_SPEC");
  const uxReviewHints = unresolvedUxReviewHints(
    detail.data.guardHints.SUBMIT_UX_REVIEW,
    checklist,
  );
  const error = createPrd.error ?? createUxSpec.error ?? transition.error;
  const requestId = error instanceof ApiError ? error.requestId : undefined;
  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <div>
          <Typography.Text className={styles.eyebrow}>REQUIREMENT DETAIL</Typography.Text>
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
      {error && (
        <Alert
          className={styles.pageAlert}
          type="error"
          content={`${error.message}${requestId ? `（requestId: ${requestId}）` : ""}`}
        />
      )}
      <Tabs defaultActiveTab="details" className={styles.tabs}>
        <Tabs.TabPane key="details" title="Requirement">
          <DetailsForm
            key={materials.data.version}
            workspaceId={workspaceId}
            projectId={projectId}
            workItemId={workItemId}
            details={materials.data}
            onChanged={invalidate}
          />
        </Tabs.TabPane>
        <Tabs.TabPane key="prd" title="PRD">
          <PrdPanel
            documentType="PRD"
            userId={userId}
            workspaceId={workspaceId}
            projectId={projectId}
            workItemId={workItemId}
            document={prd}
            onChanged={invalidate}
            create={() => createPrd.mutate()}
          />
        </Tabs.TabPane>
        <Tabs.TabPane key="ux" title="UX Spec">
          <PrdPanel
            documentType="UX Spec"
            userId={userId}
            workspaceId={workspaceId}
            projectId={projectId}
            workItemId={workItemId}
            document={uxSpec}
            onChanged={invalidate}
            create={() => createUxSpec.mutate()}
          />
        </Tabs.TabPane>
        <Tabs.TabPane key="activity" title="Activity">
          {activity.data?.map((item) => (
            <p key={`${item.kind}-${item.id}`}>
              {item.kind === "COMMENT" ? item.body : `${item.action}${item.reason ? `：${item.reason}` : ""}`}
            </p>
          ))}
        </Tabs.TabPane>
        <Tabs.TabPane key="delivery" title="Delivery Graph">
          <DeliveryGraphPanel
            workspaceId={workspaceId}
            projectId={projectId}
            workItemId={workItemId}
          />
        </Tabs.TabPane>
      </Tabs>
      {detail.data.availableActions.length > 0 && (
      <Card title="阶段操作" className={styles.actionCard}>
        <Space direction="vertical" style={{ width: "100%" }}>
          {detail.data.availableActions.includes("SUBMIT_UX_REVIEW") && (
            <UxReviewChecklist
              checklist={checklist}
              needsPublishedUxSpec={
                detail.data.guardHints.SUBMIT_UX_REVIEW?.includes("publishedUxSpec") ?? false
              }
              onChange={(item, checked) =>
                setChecklist((current) => ({ ...current, [item]: checked }))
              }
            />
          )}
          {detail.data.availableActions.map((action) => (
            <div key={action}>
              <Button
                type={action === "SUBMIT_UX_REVIEW" ? "primary" : "secondary"}
                loading={transition.isPending}
                disabled={
                  action === "SUBMIT_UX_REVIEW" &&
                  (uxSpec?.status !== "PUBLISHED" || uxReviewHints.length > 0)
                }
                onClick={() => transition.mutate(action)}
              >
                {action}
              </Button>
              {(action === "SUBMIT_UX_REVIEW"
                ? uxReviewHints
                : detail.data.guardHints[action]
              )?.length ? (
                <Typography.Text type="secondary">
                  {" "}
                  缺少：
                  {(action === "SUBMIT_UX_REVIEW"
                    ? uxReviewHints
                    : detail.data.guardHints[action]
                  )!.map(
                    (value) => missingLabel[value] ?? value,
                  ).join("、")}
                </Typography.Text>
              ) : null}
            </div>
          ))}
          {(detail.data.availableActions.includes("REJECT_PRODUCT_REVIEW")
            || detail.data.availableActions.includes("REJECT_UX_REVIEW")
            || detail.data.availableActions.includes("SKIP_UX")) && (
            <Input
              aria-label="动作原因"
              value={reason}
              onChange={setReason}
              placeholder="退回或跳过原因"
            />
          )}
        </Space>
      </Card>
      )}
      <div className={styles.phasePanels}>
      {detail.data.status === "READY_FOR_DEV" && (
        <DevTaskPanel
          workspaceId={workspaceId}
          projectId={projectId}
          requirementId={workItemId}
          onChanged={invalidate}
        />
      )}
      {(detail.data.status === "READY_FOR_DEV" || detail.data.status === "IN_DEVELOPMENT") && (
        <DevelopmentPanel
          workspaceId={workspaceId}
          projectId={projectId}
          requirementId={workItemId}
          onChanged={invalidate}
        />
      )}
      {(detail.data.status === "READY_FOR_QA" || detail.data.status === "IN_QA") && (
        <QaPanel
          workspaceId={workspaceId}
          projectId={projectId}
          requirementId={workItemId}
          onChanged={invalidate}
        />
      )}
      </div>
    </section>
  );
}

function DevTaskPanel({
  workspaceId,
  projectId,
  requirementId,
  onChanged,
}: {
  workspaceId: number;
  projectId: number;
  requirementId: number;
  onChanged: () => Promise<unknown>;
}) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const create = useMutation({
    mutationFn: () =>
      createDevTask({ workspaceId, projectId, requirementId, title, description }),
    onSuccess: () => {
      setTitle("");
      setDescription("");
      void onChanged();
    },
  });
  return (
    <Card title="Development">
      <Space direction="vertical" style={{ width: "100%" }}>
        <Input
          aria-label="Dev Task 标题"
          value={title}
          onChange={setTitle}
          placeholder="输入研发任务标题"
        />
        <Input.TextArea
          aria-label="Dev Task 说明"
          value={description}
          onChange={setDescription}
          placeholder="实现范围与约束"
        />
        <Button
          type="primary"
          disabled={!title.trim()}
          loading={create.isPending}
          onClick={() => create.mutate()}
        >
          创建 Dev Task
        </Button>
        {create.isError && <Alert type="error" content={create.error.message} />}
      </Space>
    </Card>
  );
}

function DetailsForm({
  workspaceId,
  projectId,
  workItemId,
  details,
  onChanged,
}: {
  workspaceId: number;
  projectId: number;
  workItemId: number;
  details: import("../api/work-item-api").RequirementDetails;
  onChanged: () => Promise<unknown>;
}) {
  const [form, setForm] = useState({
    goal: details.goal,
    inScope: details.inScope,
    outOfScope: details.outOfScope,
    acceptanceCriteria: details.acceptanceCriteria.join("\n"),
    businessValue: details.businessValue,
  });
  const save = useMutation({
    mutationFn: () =>
      saveRequirementDetails(workspaceId, projectId, workItemId, {
        ...form,
        acceptanceCriteria: form.acceptanceCriteria.split("\n").filter(Boolean),
        version: details.version,
      }),
    onSuccess: () => void onChanged(),
  });
  return (
    <Card>
      <Space direction="vertical" style={{ width: "100%" }}>
        <Input.TextArea
          aria-label="业务目标"
          value={form.goal}
          onChange={(value) => setForm({ ...form, goal: value })}
        />
        <Input.TextArea
          aria-label="纳入范围"
          value={form.inScope}
          onChange={(value) => setForm({ ...form, inScope: value })}
        />
        <Input.TextArea
          aria-label="排除范围"
          value={form.outOfScope}
          onChange={(value) => setForm({ ...form, outOfScope: value })}
        />
        <Input.TextArea
          aria-label="验收标准"
          placeholder="每行一条"
          value={form.acceptanceCriteria}
          onChange={(value) => setForm({ ...form, acceptanceCriteria: value })}
        />
        <Input.TextArea
          aria-label="业务价值"
          value={form.businessValue}
          onChange={(value) => setForm({ ...form, businessValue: value })}
        />
        <Button loading={save.isPending} onClick={() => save.mutate()}>
          保存材料
        </Button>
        {save.isError && <Alert type="error" content={save.error.message} />}
      </Space>
    </Card>
  );
}

export function RequirementRoute({
  workspaceSlug,
  projectKey,
  workItemId,
}: {
  workspaceSlug: string;
  projectKey: string;
  workItemId: number;
}) {
  const user = useQuery({
    queryKey: ["current-user"],
    queryFn: () => getCurrentUser(),
  });
  const workspace = user.data?.workspaces.find(
    (item) => item.slug === workspaceSlug,
  );
  const projects = useQuery({
    queryKey: ["projects", workspace?.id],
    queryFn: () => listProjects(workspace!.id),
    enabled: !!workspace,
  });
  const project = projects.data?.find((item) => item.key === projectKey);
  if (user.isPending || projects.isPending) return <Spin />;
  if (!user.data || !workspace || !project)
    return <Alert type="error" content="项目不存在或当前账户无权访问。" />;
  return (
    <RequirementDetail
      userId={user.data.id}
      workspaceId={workspace.id}
      projectId={project.id}
      workItemId={workItemId}
    />
  );
}

function PrdPanel({
  documentType,
  userId,
  workspaceId,
  projectId,
  document,
  onChanged,
  create,
}: {
  documentType: "PRD" | "UX Spec";
  userId: number;
  workspaceId: number;
  projectId: number;
  workItemId: number;
  document: import("@/features/document").Document | undefined;
  onChanged: () => Promise<unknown>;
  create: () => void;
}) {
  const versions = useQuery({
    queryKey: ["document-versions", document?.id],
    queryFn: () => listDocumentVersions(workspaceId, projectId, document!.id),
    enabled: !!document,
  });
  const save = useMutation({
    mutationFn: (content: ProseMirrorDocument) =>
      saveDocumentVersion(
        workspaceId,
        projectId,
        document!.id,
        document!.version,
        content,
      ),
    onSuccess: () => void onChanged(),
  });
  const publish = useMutation({
    mutationFn: (versionId: number) =>
      publishDocumentVersion(
        workspaceId,
        projectId,
        document!.id,
        versionId,
        document!.version,
      ),
    onSuccess: () => void onChanged(),
  });
  if (!document) return <Button onClick={create}>创建关联 {documentType}</Button>;
  const current = selectEditableVersion(versions.data, document.currentVersionId);
  return (
    <div className={styles.documentLayout}>
      <div className={styles.editorPane}>
        <div className={styles.documentHeader}>
          <div>
            <Typography.Title heading={5}>{document.title}</Typography.Title>
            <Typography.Text type="secondary">文档版本 {document.version}</Typography.Text>
          </div>
          <div className={styles.publishGroup}>
            <Tag color={document.status === "PUBLISHED" ? "green" : "orange"}>
              {document.status}
            </Tag>
            <Button
              type="primary"
              loading={publish.isPending}
              disabled={!current}
              onClick={() => current && publish.mutate(current.id)}
            >
              {current ? `发布 v${current.versionNo}` : "暂无可发布版本"}
            </Button>
          </div>
        </div>
        <div className={styles.editorSurface}>
          <DocumentEditor
            userId={userId}
            documentId={document.id}
            baseVersion={document.version}
            serverContent={
              current?.content ?? { type: "doc", content: [{ type: "paragraph" }] }
            }
            onSave={(content) => save.mutateAsync(content).then(() => undefined)}
          />
        </div>
        {(save.isError || publish.isError) && (
          <Alert type="error" content={(save.error ?? publish.error)?.message} />
        )}
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
          {!versions.isPending && versions.data?.length === 0 && (
            <Typography.Text type="secondary">保存后将在这里生成版本记录。</Typography.Text>
          )}
        </div>
      </aside>
    </div>
  );
}
