import { Alert, Button, Message, Spin } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { getCurrentUser, canEditModule } from "@/features/auth";
import { createAgentRun } from "@/features/agent-run";
import { formatRequestError } from "@/lib/api";
import { getOrganizationRequirement, statusLabel } from "@/features/work-item";
import { useState } from "react";
import { createDocument, listDocumentVersions, listWorkItemDocuments, publishDocumentVersion, saveDocumentVersion } from "../api/document-api";
import { DocumentEditor, extractDocumentOutline, scrollToSectionHeading, type DocumentOutlineItem } from "../editor/document-editor";
import type { ProseMirrorDocument } from "../editor/local-draft";
import styles from "./prd-workspace.module.css";

export function PrdWorkspace({ requirementId }: { requirementId: number }) {
  const [liveOutline, setLiveOutline] = useState<DocumentOutlineItem[] | null>(null);
  const [activeSection, setActiveSection] = useState<number | null>(null);
  const queryClient = useQueryClient();
  const user = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
  const requirement = useQuery({ queryKey: ["requirements", requirementId], queryFn: () => getOrganizationRequirement(requirementId) });
  const organizationId = user.data?.organization.id;
  const documents = useQuery({
    queryKey: ["documents", requirementId],
    queryFn: () => listWorkItemDocuments(organizationId!, requirementId),
    enabled: !!organizationId,
  });
  const prd = documents.data?.find((item) => item.type === "PRD");
  const versions = useQuery({
    queryKey: ["document-versions", prd?.id],
    queryFn: () => listDocumentVersions(organizationId!, prd!.id),
    enabled: !!organizationId && !!prd,
  });
  const current = versions.data?.[0];
  const invalidate = () => Promise.all([queryClient.invalidateQueries({ queryKey: ["documents", requirementId] }), queryClient.invalidateQueries({ queryKey: ["document-versions", prd?.id] }), queryClient.invalidateQueries({ queryKey: ["requirements", requirementId] })]);
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
  const polish = useMutation({
    mutationFn: () => createAgentRun({ workItemId: requirementId, skill: "PRODUCT", message: "请审阅当前 PRD，补充缺失的用户场景、异常流程和可验证的验收标准，并将建议写入工作项协作记录。" }),
    onSuccess: () => Message.success("ProductAgent 已开始审阅，可在执行轨迹中查看进度。"),
    onError: (error) => Message.error(formatRequestError(error)),
  });
  if (user.isPending || requirement.isPending || documents.isPending) return <Spin tip="正在加载 PRD 工作区…" />;
  const loadError = user.error ?? requirement.error ?? documents.error;
  if (loadError || !user.data || !requirement.data) return <Alert type="error" content={formatRequestError(loadError ?? new Error("无法加载 PRD 工作区"))} />;

  const published = prd?.status === "PUBLISHED";
  const canEdit = canEditModule(user.data.organization.roles, "prd");
  const outline = liveOutline ?? (current ? extractDocumentOutline(current.content) : []);
  const currentSection = activeSection ?? outline[0]?.headingIndex ?? null;

  /** 目录跳转：按标题位置定位当前正文元素并平滑滚动，跨节点跳转不受编辑器重排影响。 */
  const jumpToSection = (headingIndex: number) => {
    setActiveSection(headingIndex);
    scrollToSectionHeading(headingIndex);
  };

  return (
    <section className={styles.workspace}>
      <header className={styles.documentHead}>
        <div className={styles.headMain}>
          <p>{requirement.data.itemKey} · PRD 文档</p>
          <h1>{prd?.title ?? `${requirement.data.title} 产品需求文档`}</h1>
          <div className={styles.documentFacts} aria-label="文档信息">
            <div>
              <span>版本</span>
              <strong>v{current?.versionNo ?? 0}</strong>
            </div>
            <div>
              <span>文档状态</span>
              <strong className={published ? styles.published : styles.draft}>{published ? "已发布" : "草稿"}</strong>
            </div>
            <div>
              <span>最近更新</span>
              <strong>{formatDate(requirement.data.updatedAt)}</strong>
            </div>
            <div>
              <span>需求状态</span>
              <strong>{statusLabel[requirement.data.status] ?? requirement.data.status}</strong>
            </div>
          </div>
        </div>
        <div className={styles.headAside}>
          <div className={styles.headActions}>
            {canEdit ? (
              <>
                <Button disabled={!prd || !current || save.isPending || publish.isPending} loading={polish.isPending} onClick={() => polish.mutate()}>
                  AI 润色
                </Button>
                {prd && current && !published && (
                  <Button type="primary" disabled={save.isPending || polish.isPending} loading={publish.isPending} onClick={() => publish.mutate()}>
                    发布当前版本
                  </Button>
                )}
              </>
            ) : (
              <span className={styles.readOnlyBadge}>只读</span>
            )}
          </div>
        </div>
      </header>

      <div className={styles.columns}>
        <aside className={styles.outlineCard}>
          <header>目录</header>
          <nav aria-label="PRD 目录">
            {outline.map((item) => (
              <button type="button" key={item.headingIndex} data-level={item.level} aria-current={currentSection === item.headingIndex ? "true" : undefined} onClick={() => jumpToSection(item.headingIndex)}>
                {item.title}
              </button>
            ))}
            {outline.length === 0 && <span>添加标题后自动生成目录</span>}
          </nav>
        </aside>

        <main className={styles.editorColumn}>
          {!prd ? (
            <div className={styles.emptyEditor}>
              <div>PRD</div>
              <h2>建立这条需求的产品事实</h2>
              {canEdit ? (
                <>
                  <p>创建后可直接编辑正文、保存不可变版本，并在页面顶部发布和提交评审。</p>
                  <Button type="primary" size="large" loading={create.isPending} onClick={() => create.mutate()}>
                    创建 PRD 文档
                  </Button>
                </>
              ) : (
                <p>暂无 PRD 文档，等待产品角色创建。</p>
              )}
            </div>
          ) : versions.isPending ? (
            <Spin tip="正在加载文档版本…" />
          ) : (
            <>
              <DocumentEditor
                key={`${prd.id}-${current?.id ?? "empty"}`}
                userId={user.data.id}
                documentId={prd.id}
                baseVersion={prd.version}
                serverContent={current?.content ?? emptyDocument()}
                onSave={(content) => save.mutateAsync(content).then(() => undefined)}
                onOutlineChange={setLiveOutline}
                readOnly={!canEdit}
              />
              {save.isError && <Alert type="error" content={formatRequestError(save.error)} />}
            </>
          )}
        </main>
      </div>
    </section>
  );
}

function emptyDocument(): ProseMirrorDocument {
  return { type: "doc", content: [{ type: "heading", attrs: { level: 1 }, content: [{ type: "text", text: "1 背景与目标" }] }, { type: "paragraph" }] } as ProseMirrorDocument;
}

function formatDate(value: string) {
  return new Date(value).toLocaleString("zh-CN", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" });
}
