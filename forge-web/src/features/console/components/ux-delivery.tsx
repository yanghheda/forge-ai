"use client";

import { Alert, Button, Checkbox, Drawer, Input, Message, Spin } from "@arco-design/web-react";
import { IconCheckCircle, IconEdit, IconHistory, IconRobot, IconSend } from "@arco-design/web-react/icon";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";

import { createAgentRun } from "@/features/agent-run";
import { getCurrentUser } from "@/features/auth";
import { createDocument, DocumentEditor, listDocumentVersions, listWorkItemDocuments, publishDocumentVersion, saveDocumentVersion, type Document, type DocumentVersion, type ProseMirrorDocument } from "@/features/document";
import {
  getDeliveryGraph,
  getOrganizationRequirement,
  getRequirementActivity,
  getRequirementParticipants,
  getRequirementWorkflow,
  getWorkItem,
  listRequirementMembers,
  transitionRequirementWorkflow,
  type OrganizationRequirement,
  type RequirementActivity,
  type RequirementWorkflow,
  type RequirementWorkflowAction,
  type WorkItemDetail,
} from "@/features/work-item";
import { formatRequestError } from "@/lib/api";
import { guardHintLabel, workflowActionLabel } from "@/lib/labels";
import styles from "./ux-delivery.module.css";

const checklistKeys = ["userFlow", "pageList", "keyInteraction", "exceptionState"] as const;
type ChecklistKey = (typeof checklistKeys)[number];
type Checklist = Record<ChecklistKey, boolean>;

export interface UxWorkspaceModel {
  requirement: Pick<OrganizationRequirement, "itemKey" | "title" | "status" | "updatedAt">;
  task?: Pick<WorkItemDetail, "title" | "status" | "assigneeUserId" | "createdAt" | "updatedAt">;
  documents: Array<Pick<Document, "id" | "type" | "title" | "status" | "currentVersionId" | "version">>;
  versions: DocumentVersion[];
  activity: RequirementActivity[];
  workflow: RequirementWorkflow;
  uxDesigner: string;
  reviewer: string;
}

export function UxDelivery({ requirementId }: { requirementId: number }) {
  const queryClient = useQueryClient();
  const [editorOpen, setEditorOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [checklist, setChecklist] = useState<Checklist>({ userFlow: false, pageList: false, keyInteraction: false, exceptionState: false });
  const user = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
  const requirement = useQuery({ queryKey: ["requirements", requirementId], queryFn: () => getOrganizationRequirement(requirementId) });
  const graph = useQuery({ queryKey: ["delivery-graph", requirementId], queryFn: () => getDeliveryGraph(requirementId) });
  const workflow = useQuery({ queryKey: ["requirement-workflow", requirementId], queryFn: () => getRequirementWorkflow(requirementId) });
  const activity = useQuery({ queryKey: ["requirement-activity", requirementId], queryFn: () => getRequirementActivity(requirementId) });
  const participants = useQuery({ queryKey: ["requirement-participants", requirementId], queryFn: () => getRequirementParticipants(requirementId) });
  const members = useQuery({ queryKey: ["requirement-members"], queryFn: () => listRequirementMembers() });
  const documents = useQuery({
    queryKey: ["documents", requirementId],
    queryFn: () => listWorkItemDocuments(user.data!.organization.id, requirementId),
    enabled: !!user.data,
  });
  const uxTaskId = graph.data?.nodes.find((node) => node.kind === "WORK_ITEM" && node.type === "UX_TASK")?.resourceId;
  const task = useQuery({ queryKey: ["work-item", uxTaskId], queryFn: () => getWorkItem(uxTaskId!), enabled: !!uxTaskId });
  const uxDocuments = (documents.data ?? []).filter((document) => ["UX_SPEC", "PROTOTYPE_SPEC", "DESIGN_GUIDE"].includes(document.type));
  const versionQueries = useQueries({
    queries: uxDocuments.map((document) => ({ queryKey: ["document-versions", document.id], queryFn: () => listDocumentVersions(user.data!.organization.id, document.id), enabled: !!user.data })),
  });
  const versions = versionQueries.flatMap((query) => query.data ?? []).sort((a, b) => b.versionNo - a.versionNo);
  const uxSpec = uxDocuments.find((document) => document.type === "UX_SPEC");
  const editableVersion = versions.find((version) => version.documentId === uxSpec?.id);
  const invalidate = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ["requirements", requirementId] }),
      queryClient.invalidateQueries({ queryKey: ["requirement-workflow", requirementId] }),
      queryClient.invalidateQueries({ queryKey: ["requirement-activity", requirementId] }),
      queryClient.invalidateQueries({ queryKey: ["delivery-graph", requirementId] }),
      queryClient.invalidateQueries({ queryKey: ["documents", requirementId] }),
      queryClient.invalidateQueries({ queryKey: ["document-versions", uxSpec?.id] }),
    ]);
  const transition = useMutation({
    mutationFn: (action: RequirementWorkflowAction) =>
      transitionRequirementWorkflow(requirementId, action, workflow.data!.version, {
        reason: action === "REJECT_UX_REVIEW" ? reason.trim() : undefined,
        checklist: action === "SUBMIT_UX_REVIEW" ? checklistKeys.filter((key) => checklist[key]) : undefined,
      }),
    onSuccess: (_, action) => {
      Message.success(`${workflowActionLabel(action)}成功`);
      setReason("");
      void invalidate();
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const generate = useMutation({
    mutationFn: () => createAgentRun({ workItemId: requirementId, skill: "UX", message: "请基于当前已发布 PRD 生成或完善 UX Spec，覆盖用户流、页面清单、关键交互和异常状态，并保存为需求关联文档。" }),
    onSuccess: () => Message.success("UXAgent 已开始生成，可在执行轨迹中查看进度。"),
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const createSpec = useMutation({
    mutationFn: () => createDocument({ organizationId: user.data!.organization.id, workItemId: requirementId, type: "UX_SPEC", title: `${requirement.data!.itemKey} ${requirement.data!.title} UX Spec` }),
    onSuccess: () => {
      Message.success("UX Spec 已创建，可以开始编辑。");
      void invalidate();
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const saveSpec = useMutation({
    mutationFn: (content: ProseMirrorDocument) => saveDocumentVersion(user.data!.organization.id, uxSpec!.id, uxSpec!.version, content),
    onSuccess: () => {
      Message.success("已保存新的不可变 UX Spec 版本。");
      void invalidate();
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const publishSpec = useMutation({
    mutationFn: () => publishDocumentVersion(user.data!.organization.id, uxSpec!.id, editableVersion!.id, uxSpec!.version),
    onSuccess: () => {
      Message.success("UX Spec 当前版本已发布，可以提交评审。");
      void invalidate();
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });

  const pending = [user, requirement, graph, workflow, activity, participants, members, documents].some((query) => query.isPending);
  const error = [user, requirement, graph, workflow, activity, participants, members, documents].find((query) => query.error)?.error;
  if (pending) return <Spin tip="正在加载 UX 工作区…" />;
  if (error || !user.data || !requirement.data || !workflow.data) return <Alert type="error" content={formatRequestError(error ?? new Error("无法加载 UX 工作区"))} />;
  const uxParticipant = participants.data?.find((item) => item.role === "UX");
  const latestReview = [...(activity.data ?? [])].reverse().find((item) => item.action?.includes("UX_REVIEW"));
  const reviewer = members.data?.find((item) => item.userId === latestReview?.actorId)?.displayName ?? "等待评审人";
  return (
    <>
      <UxWorkspaceView
        model={{ requirement: requirement.data, task: task.data, documents: uxDocuments, versions, activity: activity.data ?? [], workflow: workflow.data, uxDesigner: uxParticipant?.displayName ?? "未指派", reviewer }}
        checklist={checklist}
        busy={transition.isPending || generate.isPending}
        reason={reason}
        onChecklistChange={(key, checked) => setChecklist((value) => ({ ...value, [key]: checked }))}
        onReasonChange={setReason}
        onAction={(action) => transition.mutate(action)}
        onGenerate={() => generate.mutate()}
        onEdit={() => setEditorOpen(true)}
      />
      <Drawer
        width="min(920px, 92vw)"
        visible={editorOpen}
        title={uxSpec?.title ?? "新建 UX Spec"}
        onCancel={() => setEditorOpen(false)}
        footer={
          <>
            <Button onClick={() => setEditorOpen(false)}>关闭</Button>
            {uxSpec && editableVersion && uxSpec.status !== "PUBLISHED" && (
              <Button type="primary" loading={publishSpec.isPending} onClick={() => publishSpec.mutate()}>
                发布当前版本
              </Button>
            )}
          </>
        }
      >
        {!uxSpec ? (
          <div className={styles.editorEmpty}>
            <h2>创建 UX Spec</h2>
            <p>创建后可编辑正文、保存不可变版本并发布，再提交 UX 评审。</p>
            <Button type="primary" loading={createSpec.isPending} onClick={() => createSpec.mutate()}>
              创建 UX Spec
            </Button>
          </div>
        ) : (
          <DocumentEditor key={`${uxSpec.id}-${editableVersion?.id ?? "empty"}`} userId={user.data.id} documentId={uxSpec.id} baseVersion={uxSpec.version} serverContent={editableVersion?.content ?? emptyDocument()} onSave={(content) => saveSpec.mutateAsync(content).then(() => undefined)} />
        )}
      </Drawer>
    </>
  );
}

export function UxWorkspaceView({
  model,
  checklist,
  busy,
  reason,
  onChecklistChange,
  onReasonChange,
  onAction,
  onGenerate,
  onEdit,
}: {
  model: UxWorkspaceModel;
  checklist: Checklist;
  busy: boolean;
  reason: string;
  onChecklistChange: (key: ChecklistKey, checked: boolean) => void;
  onReasonChange: (value: string) => void;
  onAction: (action: RequirementWorkflowAction) => void;
  onGenerate: () => void;
  onEdit: () => void;
}) {
  const currentVersion = model.versions[0];
  const latestReview = [...model.activity].reverse().find((item) => item.action?.includes("UX_REVIEW"));
  const missing = model.workflow.guardHints.SUBMIT_UX_REVIEW ?? [];
  const approved = ["READY_FOR_DEV", "IN_DEVELOPMENT", "READY_FOR_QA", "IN_QA", "READY_FOR_RELEASE", "RELEASED", "DONE"].includes(model.requirement.status);
  return (
    <div className={styles.workspace}>
      <header className={styles.head}>
        <div>
          <h1>UX 工作区</h1>
          <p>
            {currentVersion ? `UX 文档 v${currentVersion.versionNo}` : "尚无 UX 文档版本"}
            <i />
            {approved ? "评审已通过" : model.requirement.status}
            <i />
            设计师 {model.uxDesigner}
          </p>
        </div>
        <div>
          <Button icon={<IconHistory />}>查看评审历史</Button>
          <Button icon={<IconEdit />} onClick={onEdit}>
            编辑 UX Spec
          </Button>
          <Button type="primary" icon={<IconRobot />} loading={busy} onClick={onGenerate}>
            AI 生成画板
          </Button>
        </div>
      </header>
      <div className={styles.grid}>
        <aside className={styles.stack}>
          <Panel title="UX 任务" extra={<Status value={model.task?.status ?? "未创建"} />}>
            <dl className={styles.kv}>
              <Row label="指派" value={model.uxDesigner} />
              <Row label="开始" value={formatDate(model.task?.createdAt)} />
              <Row label="更新" value={formatDate(model.task?.updatedAt)} />
              <Row label="关联需求" value={model.requirement.itemKey} />
            </dl>
            <div className={styles.progress}>
              <span style={{ width: approved ? "100%" : model.task?.status === "IN_REVIEW" ? "80%" : "40%" }} />
            </div>
          </Panel>
          <Panel title="文档版本" extra={`共 ${model.versions.length} 个版本`}>
            <div className={styles.versions}>
              {model.versions.map((version, index) => (
                <div className={index === 0 ? styles.selectedVersion : ""} key={version.id}>
                  <IconCheckCircle />
                  <span>
                    <b>v{version.versionNo}</b>
                    <small>{index === 0 ? "当前交付版本" : "历史版本"}</small>
                  </span>
                  <time>{formatDate(version.createdAt)}</time>
                </div>
              ))}
              {model.versions.length === 0 && <Empty text="尚未保存 UX 文档版本" />}
            </div>
          </Panel>
          <Panel title="设计检查点" extra={`${checklistKeys.filter((key) => checklist[key]).length}/4`}>
            <div className={styles.checks}>
              {checklistKeys.map((key) => (
                <Checkbox key={key} checked={checklist[key]} onChange={(checked) => onChecklistChange(key, checked)}>
                  {guardHintLabel(key)}
                </Checkbox>
              ))}
            </div>
            {missing.length > 0 && <p className={styles.warning}>仍缺少：{missing.map(guardHintLabel).join("、")}</p>}
          </Panel>
        </aside>
        <main className={styles.canvas}>
          <div className={styles.toolbar}>
            <b>
              画板 <em>{model.documents.length}</em>
            </b>
            <span>画板</span>
            <span>列表</span>
            <Button size="small" icon={<IconRobot />} onClick={onGenerate}>
              AI 生成画板
            </Button>
          </div>
          <div className={styles.boards}>
            {model.documents.map((document, index) => (
              <article className={index === 0 ? styles.selectedBoard : ""} key={document.id}>
                <Wireframe variant={index % 3} />
                <h3>{document.title}</h3>
                <footer>
                  <span>{document.type}</span>
                  <Status value={document.status} />
                </footer>
              </article>
            ))}
            {model.documents.length === 0 && <Empty text="尚无 UX 交付物，请创建 UX Spec 或让 UXAgent 生成草稿。" />}
          </div>
          <section className={styles.notes}>
            <b>设计说明</b>
            <p>{currentVersion?.plainText || "UX 文档保存后，这里展示当前版本的真实文本摘要。"}</p>
          </section>
        </main>
        <aside className={styles.stack}>
          <Panel title="评审结论" extra={<Link href={`/agent/trace/${latestReview?.id ?? ""}`}>查看轨迹</Link>}>
            <div className={approved ? styles.verdictSuccess : styles.verdict}>
              <IconCheckCircle />
              {approved ? "评审通过" : latestReview ? workflowActionLabel(latestReview.action ?? "") : "等待提交评审"}
            </div>
            <p className={styles.muted}>
              {model.reviewer} · {formatDate(latestReview?.createdAt)}
              {currentVersion ? ` · UX 文档 v${currentVersion.versionNo}` : ""}
            </p>
            {latestReview?.reason && <Alert type="warning" content={latestReview.reason} />}
            {model.workflow.availableActions.includes("REJECT_UX_REVIEW") && <Input.TextArea aria-label="退回原因" value={reason} onChange={onReasonChange} placeholder="退回时请填写具体修改意见" />}
            <div className={styles.actions}>
              {model.workflow.availableActions
                .filter((action) => action.includes("UX_REVIEW"))
                .map((action) => (
                  <Button
                    key={action}
                    type={action.startsWith("APPROVE") || action.startsWith("SUBMIT") ? "primary" : "secondary"}
                    icon={action.startsWith("SUBMIT") ? <IconSend /> : undefined}
                    loading={busy}
                    disabled={(action === "REJECT_UX_REVIEW" && !reason.trim()) || (action === "SUBMIT_UX_REVIEW" && checklistKeys.some((key) => !checklist[key]))}
                    onClick={() => onAction(action)}
                  >
                    {workflowActionLabel(action)}
                  </Button>
                ))}
            </div>
          </Panel>
          <Panel title="UXAgent 生成记录" extra="UXAgent">
            <ol className={styles.timeline}>
              {model.activity
                .slice(-6)
                .reverse()
                .map((item) => (
                  <li key={`${item.kind}-${item.id}`}>
                    <b>{item.action ? workflowActionLabel(item.action) : "协作评论"}</b>
                    <time>{formatDate(item.createdAt)}</time>
                    <p>{item.reason || item.body || "工作流事件已记录"}</p>
                  </li>
                ))}
              {model.activity.length === 0 && <Empty text="暂无生成或评审记录" />}
            </ol>
          </Panel>
          <Alert type="info" content="阶段推进由服务端 Guard 校验；缺少已发布 UX Spec 或四项 checklist 时不能进入开发。" />
        </aside>
      </div>
    </div>
  );
}

function Panel({ title, extra, children }: { title: string; extra?: React.ReactNode; children: React.ReactNode }) {
  return (
    <section className={styles.panel}>
      <header>
        <b>{title}</b>
        <span>{extra}</span>
      </header>
      <div className={styles.panelBody}>{children}</div>
    </section>
  );
}
function Row({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
function Status({ value }: { value: string }) {
  return <span className={value === "PUBLISHED" || value === "DONE" || value.includes("APPROVED") ? styles.successTag : styles.statusTag}>{value}</span>;
}
function Empty({ text }: { text: string }) {
  return <p className={styles.empty}>{text}</p>;
}
function Wireframe({ variant }: { variant: number }) {
  return (
    <div className={styles.wireframe}>
      <div className={styles.browser}>
        <i />
        <i />
        <i />
        <span />
      </div>
      <div className={styles.wirebody}>
        {variant !== 1 && (
          <aside>
            <i />
            <i />
            <i />
            <i />
          </aside>
        )}
        <main>
          <b />
          <span />
          <span />
          <span />
        </main>
      </div>
    </div>
  );
}
function formatDate(value?: string | null) {
  return value ? new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date(value)) : "—";
}
function emptyDocument(): ProseMirrorDocument {
  return {
    type: "doc",
    content: [
      { type: "heading", attrs: { level: 1 }, content: [{ type: "text", text: "UX Spec" }] },
      { type: "paragraph", content: [{ type: "text", text: "从用户流、页面清单、关键交互和异常状态开始完善设计说明。" }] },
    ],
  };
}
