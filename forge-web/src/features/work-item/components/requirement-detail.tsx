"use client";

import {
  Alert,
  Button,
  Card,
  Input,
  Space,
  Spin,
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
} from "@/features/document";
import {
  getRequirementDetails,
  getWorkItem,
  getWorkItemActivity,
  saveRequirementDetails,
  transitionRequirement,
  type WorkflowAction,
} from "../api/work-item-api";

const missingLabel: Record<string, string> = {
  goal: "业务目标",
  inScope: "范围",
  acceptanceCriteria: "验收标准",
  publishedPrd: "已发布 PRD",
  publishedUxSpec: "已发布 UX Spec",
  reason: "退回原因",
};
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
  const error = createPrd.error ?? createUxSpec.error ?? transition.error;
  const requestId = error instanceof ApiError ? error.requestId : undefined;
  return (
    <section>
      <Typography.Title heading={2}>
        {detail.data.itemKey} · {detail.data.title}
      </Typography.Title>
      <Typography.Text>
        {detail.data.status} · version {detail.data.version}
      </Typography.Text>
      {error && (
        <Alert
          type="error"
          content={`${error.message}${requestId ? `（requestId: ${requestId}）` : ""}`}
        />
      )}
      <Tabs defaultActiveTab="details">
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
      </Tabs>
      <Card title="Product actions">
        <Space direction="vertical">
          {detail.data.availableActions.map((action) => (
            <div key={action}>
              <Button
                loading={transition.isPending}
                onClick={() => transition.mutate(action)}
              >
                {action}
              </Button>
              {detail.data.guardHints[action]?.length ? (
                <Typography.Text type="secondary">
                  {" "}
                  缺少：
                  {detail.data.guardHints[action]!.map(
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
          {detail.data.availableActions.includes("SUBMIT_UX_REVIEW") && (
            <Space direction="vertical">
              {Object.keys(checklist).map((item) => (
                <label key={item}>
                  <input
                    type="checkbox"
                    checked={checklist[item as keyof typeof checklist]}
                    onChange={(event) =>
                      setChecklist({ ...checklist, [item]: event.target.checked })
                    }
                  />
                  {missingLabel[item] ?? item}
                </label>
              ))}
            </Space>
          )}
        </Space>
      </Card>
    </section>
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
  userId,
  workspaceId,
  projectId,
  document,
  onChanged,
  create,
}: {
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
    mutationFn: () =>
      publishDocumentVersion(
        workspaceId,
        projectId,
        document!.id,
        document!.currentVersionId!,
        document!.version,
      ),
    onSuccess: () => void onChanged(),
  });
  if (!document) return <Button onClick={create}>创建关联 PRD</Button>;
  const current = versions.data?.find(
    (version) => version.id === document.currentVersionId,
  );
  return (
    <Space direction="vertical">
      <Typography.Text>
        {document.title} · {document.status} · version {document.version}
      </Typography.Text>
      <DocumentEditor
        userId={userId}
        documentId={document.id}
        baseVersion={document.version}
        serverContent={
          current?.content ?? { type: "doc", content: [{ type: "paragraph" }] }
        }
        onSave={(content) => save.mutateAsync(content).then(() => undefined)}
      />
      <Button
        disabled={!document.currentVersionId}
        onClick={() => publish.mutate()}
      >
        发布当前版本
      </Button>
      {versions.data?.map((version) => (
        <Typography.Text key={version.id}>
          历史 v{version.versionNo} · {version.contentHash.slice(0, 8)}
        </Typography.Text>
      ))}
    </Space>
  );
}
