"use client";

import { Alert, Button, Input, Message, Spin } from "@arco-design/web-react";
import { IconCheck, IconHistory, IconRobot, IconSend } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { getCurrentUser } from "@/features/auth";
import { createAgentRun } from "@/features/agent-run";
import { formatRequestError } from "@/lib/api";
import { guardHintLabel } from "@/lib/labels";
import { createRequirementComment, getOrganizationRequirement, getRequirementActivity, getRequirementWorkflow, transitionRequirementWorkflow } from "@/features/work-item";
import { useState } from "react";
import { createDocument, listDocumentVersions, listWorkItemDocuments, publishDocumentVersion, saveDocumentVersion, type DocumentVersion } from "../api/document-api";
import { DocumentEditor } from "../editor/document-editor";
import type { ProseMirrorDocument } from "../editor/local-draft";
import styles from "./prd-workspace.module.css";

export function PrdWorkspace({ requirementId }: { requirementId: number }) {
  const [comment, setComment] = useState("");
  const queryClient = useQueryClient();
  const user = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
  const requirement = useQuery({ queryKey: ["requirements", requirementId], queryFn: () => getOrganizationRequirement(requirementId) });
  const workflow = useQuery({ queryKey: ["requirement-workflow", requirementId], queryFn: () => getRequirementWorkflow(requirementId) });
  const organizationId = user.data?.organization.id;
  const documents = useQuery({
    queryKey: ["documents", requirementId],
    queryFn: () => listWorkItemDocuments(organizationId!, requirementId),
    enabled: !!organizationId,
  });
  const activity = useQuery({ queryKey: ["requirement-activity", requirementId], queryFn: () => getRequirementActivity(requirementId) });
  const prd = documents.data?.find((item) => item.type === "PRD");
  const versions = useQuery({
    queryKey: ["document-versions", prd?.id],
    queryFn: () => listDocumentVersions(organizationId!, prd!.id),
    enabled: !!organizationId && !!prd,
  });
  const current = versions.data?.[0];
  const invalidate = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ["documents", requirementId] }),
      queryClient.invalidateQueries({ queryKey: ["document-versions", prd?.id] }),
      queryClient.invalidateQueries({ queryKey: ["requirement-workflow", requirementId] }),
      queryClient.invalidateQueries({ queryKey: ["requirements", requirementId] }),
      queryClient.invalidateQueries({ queryKey: ["requirement-activity", requirementId] }),
    ]);
  const create = useMutation({
    mutationFn: () => createDocument({ organizationId: organizationId!, workItemId: requirementId, type: "PRD", title: `${requirement.data!.itemKey} ${requirement.data!.title} 产品需求文档` }),
    onSuccess: () => void invalidate(),
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const save = useMutation({
    mutationFn: (content: ProseMirrorDocument) => saveDocumentVersion(organizationId!, prd!.id, prd!.version, content),
    onSuccess: () => {
      Message.success("已保存为新的不可变版本。");
      void invalidate();
    },
  });
  const publish = useMutation({
    mutationFn: () => publishDocumentVersion(organizationId!, prd!.id, current!.id, prd!.version),
    onSuccess: () => {
      Message.success("PRD 版本已发布，可以提交产品评审。");
      void invalidate();
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const submitReview = useMutation({
    mutationFn: () => transitionRequirementWorkflow(requirementId, "SUBMIT_PRODUCT_REVIEW", workflow.data!.version),
    onSuccess: () => {
      Message.success("已提交产品评审。");
      void invalidate();
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const polish = useMutation({
    mutationFn: () => createAgentRun({ workItemId: requirementId, skill: "PRODUCT", message: "请审阅当前 PRD，补充缺失的用户场景、异常流程和可验证的验收标准，并将建议写入工作项协作记录。" }),
    onSuccess: () => Message.success("ProductAgent 已开始审阅，可在执行轨迹中查看进度。"),
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const sendComment = useMutation({
    mutationFn: () => createRequirementComment(requirementId, comment.trim()),
    onSuccess: () => {
      setComment("");
      void queryClient.invalidateQueries({ queryKey: ["requirement-activity", requirementId] });
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });

  if (user.isPending || requirement.isPending || documents.isPending || workflow.isPending) return <Spin tip="正在加载 PRD 工作区…" />;
  const loadError = user.error ?? requirement.error ?? documents.error ?? workflow.error;
  if (loadError || !user.data || !requirement.data) return <Alert type="error" content={formatRequestError(loadError ?? new Error("无法加载 PRD 工作区"))} />;

  const published = prd?.status === "PUBLISHED";
  const missing = workflow.data?.guardHints.SUBMIT_PRODUCT_REVIEW ?? [];
  const canSubmit = canSubmitProductReview(Boolean(published), workflow.data?.availableActions.includes("SUBMIT_PRODUCT_REVIEW") ?? false, missing);
  const outline = extractOutline(current);

  return (
    <section className={styles.workspace}>
      <header className={styles.documentHead}>
        <div>
          <p>{requirement.data.itemKey} · PRD 文档</p>
          <h1>{prd?.title ?? `${requirement.data.title} 产品需求文档`}</h1>
          <div className={styles.meta}>
            <span>PRD v{current?.versionNo ?? 0}</span>
            <span className={published ? styles.published : styles.draft}>{published ? "已发布" : "草稿"}</span>
            <span>{prd ? "编辑内容会先保存为新版本" : "创建文档后即可开始编辑"}</span>
          </div>
        </div>
        <div className={styles.headActions}>
          <Button icon={<IconHistory />}>历史版本 {versions.data?.length ?? 0}</Button>
          <Button loading={polish.isPending} onClick={() => polish.mutate()}>
            AI 润色
          </Button>
          {prd && current && !published && (
            <Button type="primary" loading={publish.isPending} onClick={() => publish.mutate()}>
              发布当前版本
            </Button>
          )}
          <Button type="primary" icon={<IconSend />} loading={submitReview.isPending} disabled={!canSubmit} onClick={() => submitReview.mutate()}>
            提交产品评审
          </Button>
        </div>
      </header>

      <div className={styles.columns}>
        <aside className={styles.outlineCard}>
          <header>目录</header>
          <nav aria-label="PRD 目录">
            {outline.map((item, index) => (
              <a key={`${item}-${index}`} href={`#prd-section-${index + 1}`}>
                {item}
              </a>
            ))}
            {outline.length === 0 && <span>添加标题后自动生成目录</span>}
          </nav>
        </aside>

        <main className={styles.editorColumn}>
          {!prd ? (
            <div className={styles.emptyEditor}>
              <div>PRD</div>
              <h2>建立这条需求的产品事实</h2>
              <p>创建后可直接编辑正文、保存不可变版本，并从右侧推进到产品评审。</p>
              <Button type="primary" size="large" loading={create.isPending} onClick={() => create.mutate()}>
                创建 PRD 文档
              </Button>
            </div>
          ) : versions.isPending ? (
            <Spin tip="正在加载文档版本…" />
          ) : (
            <>
              <DocumentEditor key={`${prd.id}-${current?.id ?? "empty"}`} userId={user.data.id} documentId={prd.id} baseVersion={prd.version} serverContent={current?.content ?? emptyDocument()} onSave={(content) => save.mutateAsync(content).then(() => undefined)} />
              {save.isError && <Alert type="error" content={formatRequestError(save.error)} />}
            </>
          )}
        </main>

        <aside className={styles.sideColumn}>
          <section className={styles.infoCard}>
            <header>文档信息</header>
            <dl>
              <div>
                <dt>版本</dt>
                <dd>v{current?.versionNo ?? 0}</dd>
              </div>
              <div>
                <dt>状态</dt>
                <dd className={published ? styles.successText : ""}>{published ? "已发布" : "草稿"}</dd>
              </div>
              <div>
                <dt>最近更新</dt>
                <dd>{formatDate(requirement.data.updatedAt)}</dd>
              </div>
              <div>
                <dt>需求状态</dt>
                <dd>{requirement.data.status}</dd>
              </div>
            </dl>
          </section>
          <section className={styles.progressCard}>
            <header>
              <IconRobot /> 产品推进
            </header>
            <div className={styles.progressBody}>
              <div className={styles.step}>
                <IconCheck />
                <span>
                  <b>编写 PRD</b>
                  <small>{prd ? "文档已创建" : "等待创建文档"}</small>
                </span>
              </div>
              <div className={styles.step}>
                <IconCheck />
                <span>
                  <b>发布版本</b>
                  <small>{published ? "当前版本已发布" : "保存后发布当前版本"}</small>
                </span>
              </div>
              <div className={styles.step}>
                <IconSend />
                <span>
                  <b>产品评审</b>
                  <small>进入评审后可批准或退回修改</small>
                </span>
              </div>
              <Button type="primary" long icon={<IconSend />} loading={submitReview.isPending} disabled={!canSubmit} onClick={() => submitReview.mutate()}>
                提交产品评审
              </Button>
              {!published && <p className={styles.hint}>请先发布 PRD 版本，再提交产品评审。</p>}
              {published && missing.length > 0 && <p className={styles.hint}>仍缺少提交条件：{missing.map(guardHintLabel).join("、")}。请先在需求详情中补充后再提交。</p>}
              {published && !workflow.data?.availableActions.includes("SUBMIT_PRODUCT_REVIEW") && <p className={styles.hint}>当前状态无需再次提交产品评审。</p>}
            </div>
          </section>
          <section className={styles.agentCard}>
            <header>
              <span className={styles.agentAvatar}>✦</span>
              <b>ProductAgent</b>
              <i />
            </header>
            <div className={styles.activityList}>
              {(activity.data ?? []).slice(-4).map((item) => (
                <div className={item.actorId === user.data.id ? styles.ownMessage : styles.agentMessage} key={`${item.kind}-${item.id}`}>
                  <p>{item.kind === "COMMENT" ? item.body : `${item.action}${item.reason ? `：${item.reason}` : ""}`}</p>
                  <time>{new Date(item.createdAt).toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" })}</time>
                </div>
              ))}
              {activity.data?.length === 0 && <p className={styles.emptyActivity}>在这里记录评审意见，或使用 AI 润色发起 ProductAgent 审阅。</p>}
            </div>
            <div className={styles.commentBox}>
              <Input.TextArea aria-label="协作消息" autoSize={{ minRows: 2, maxRows: 4 }} value={comment} onChange={setComment} placeholder="补充评审意见…" />
              <Button type="primary" disabled={!comment.trim()} loading={sendComment.isPending} onClick={() => sendComment.mutate()}>
                发送
              </Button>
            </div>
          </section>
        </aside>
      </div>
    </section>
  );
}

function emptyDocument(): ProseMirrorDocument {
  return { type: "doc", content: [{ type: "heading", attrs: { level: 1 }, content: [{ type: "text", text: "1 背景与目标" }] }, { type: "paragraph" }] } as ProseMirrorDocument;
}

function extractOutline(version?: DocumentVersion): string[] {
  const nodes = (version?.content as { content?: Array<{ type?: string; content?: Array<{ text?: string }> }> } | undefined)?.content ?? [];
  return nodes
    .filter((node) => node.type === "heading")
    .map((node) => node.content?.map((part) => part.text ?? "").join("") ?? "")
    .filter(Boolean);
}

function formatDate(value: string) {
  return new Date(value).toLocaleString("zh-CN", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

export function canSubmitProductReview(published: boolean, actionAvailable: boolean, missing: string[]): boolean {
  return published && actionAvailable && missing.length === 0;
}
