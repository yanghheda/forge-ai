import StarterKit from "@tiptap/starter-kit";
import { EditorContent, useEditor, type Editor } from "@tiptap/react";
import { useState } from "react";
import type { ProseMirrorDocument } from "./local-draft";
import { clearDraft, loadDraft, saveDraft } from "./local-draft";
import styles from "./document-editor.module.css";

interface DocumentEditorProps {
  userId: number;
  documentId: number;
  baseVersion: number;
  serverContent: ProseMirrorDocument;
  onSave(content: ProseMirrorDocument): Promise<void>;
  onOutlineChange?(outline: DocumentOutlineItem[]): void;
  /* 非对应角色打开文档时进入只读视图：不加载本地草稿、不写入正文、不提供保存入口。 */
  readOnly?: boolean;
}

export interface DocumentOutlineItem {
  /* 该标题在正文全部标题中的序号，从 0 开始；目录跳转按此序号定位标题元素。 */
  headingIndex: number;
  level: number;
  title: string;
}

export function DocumentEditor({ userId, documentId, baseVersion, serverContent, onSave, onOutlineChange, readOnly = false }: DocumentEditorProps) {
  const [draft, setDraft] = useState(() => (readOnly ? null : loadDraft(userId, documentId, baseVersion)));
  const [dirty, setDirty] = useState(() => Boolean(draft));
  const [saving, setSaving] = useState(false);
  const [savedAt, setSavedAt] = useState<Date | null>(null);
  const editor = useEditor({
    extensions: [StarterKit],
    content: draft ?? serverContent,
    editable: !readOnly,
    onCreate: ({ editor: instance }) => syncOutline(instance, onOutlineChange),
    onUpdate: ({ editor: instance }) => {
      if (readOnly) {
        return;
      }
      const content = instance.getJSON() as ProseMirrorDocument;
      setDirty(true);
      setDraft(content);
      saveDraft(userId, documentId, baseVersion, content);
      syncOutline(instance, onOutlineChange);
    },
  });
  if (!editor) {
    return null;
  }
  const save = async () => {
    setSaving(true);
    try {
      await onSave(editor.getJSON() as ProseMirrorDocument);
      setDirty(false);
      setDraft(null);
      clearDraft(userId, documentId, baseVersion);
      setSavedAt(new Date());
    } finally {
      setSaving(false);
    }
  };
  return (
    <section aria-label="文档编辑器" className={readOnly ? `${styles.editor} ${styles.readOnly}` : styles.editor}>
      {draft && (
        <p className={styles.draftAlert} role="alert">
          检测到本地草稿，请确认内容后再保存为新版本。
        </p>
      )}
      <div className={styles.toolbar} aria-label="文档格式工具栏">
        <div className={styles.formatActions}>
          <div className={styles.toolGroup}>
            <button type="button" aria-label="正文" aria-pressed={editor.isActive("paragraph")} disabled={readOnly} onClick={() => editor.chain().focus().setParagraph().run()}>
              正文
            </button>
            <button type="button" aria-label="二级标题" aria-pressed={editor.isActive("heading", { level: 2 })} disabled={readOnly} onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}>
              H2
            </button>
            <button type="button" aria-label="一级标题" aria-pressed={editor.isActive("heading", { level: 1 })} disabled={readOnly} onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}>
              H1
            </button>
          </div>
          <span className={styles.separator} />
          <div className={styles.toolGroup}>
            <button type="button" aria-label="粗体" aria-pressed={editor.isActive("bold")} disabled={readOnly} onClick={() => editor.chain().focus().toggleBold().run()}>
              <strong>B</strong>
            </button>
            <button type="button" aria-label="斜体" aria-pressed={editor.isActive("italic")} disabled={readOnly} onClick={() => editor.chain().focus().toggleItalic().run()}>
              <em>I</em>
            </button>
            <button type="button" aria-label="删除线" aria-pressed={editor.isActive("strike")} disabled={readOnly} onClick={() => editor.chain().focus().toggleStrike().run()}>
              <s>S</s>
            </button>
            <button type="button" aria-label="无序列表" aria-pressed={editor.isActive("bulletList")} disabled={readOnly} onClick={() => editor.chain().focus().toggleBulletList().run()}>
              • 列表
            </button>
            <button type="button" aria-label="有序列表" aria-pressed={editor.isActive("orderedList")} disabled={readOnly} onClick={() => editor.chain().focus().toggleOrderedList().run()}>
              1. 列表
            </button>
            <button type="button" aria-label="引用" aria-pressed={editor.isActive("blockquote")} disabled={readOnly} onClick={() => editor.chain().focus().toggleBlockquote().run()}>
              “ ”
            </button>
            <button type="button" aria-label="代码块" aria-pressed={editor.isActive("codeBlock")} disabled={readOnly} onClick={() => editor.chain().focus().toggleCodeBlock().run()}>
              {"</>"}
            </button>
          </div>
          <span className={styles.separator} />
          <div className={styles.toolGroup}>
            <button type="button" aria-label="撤销" disabled={readOnly} onClick={() => editor.chain().focus().undo().run()}>
              ↶
            </button>
            <button type="button" aria-label="重做" disabled={readOnly} onClick={() => editor.chain().focus().redo().run()}>
              ↷
            </button>
          </div>
        </div>
        <div className={styles.saveActions}>
          {readOnly ? (
            <span className={styles.readOnlyBadge}>只读</span>
          ) : (
            <>
              <span className={dirty ? styles.unsaved : styles.saved}>{dirty ? "有未保存的修改" : savedAt ? `已保存于 ${savedAt.toLocaleTimeString()}` : "修改后可保存新版本"}</span>
              <button className={styles.saveButton} type="button" disabled={saving || !dirty} onClick={() => void save()}>
                {saving ? "正在保存…" : "保存为新版本"}
              </button>
            </>
          )}
        </div>
      </div>
      <div className={styles.paper}>
        <EditorContent editor={editor} />
      </div>
    </section>
  );
}

export function extractDocumentOutline(content: ProseMirrorDocument): DocumentOutlineItem[] {
  return (content.content ?? [])
    .filter((node) => node.type === "heading")
    .map((node, headingIndex) => ({
      headingIndex,
      level: Number(node.attrs?.level ?? 1),
      title:
        node.content
          ?.map((part) => part.text ?? "")
          .join("")
          .trim() ?? "",
    }))
    .filter((item) => item.title.length > 0);
}

function syncOutline(editor: Editor, onOutlineChange?: (outline: DocumentOutlineItem[]) => void) {
  onOutlineChange?.(extractDocumentOutline(editor.getJSON() as ProseMirrorDocument));
}

/**
 * 滚动到正文第 headingIndex 个标题。
 * 编辑器每次重排正文都会重建标题元素，因此这里按位置即时定位，不依赖标题上的 id。
 */
export function scrollToSectionHeading(headingIndex: number): boolean {
  const headings = document.querySelectorAll<HTMLElement>(`[aria-label="文档编辑器"] h1, [aria-label="文档编辑器"] h2, [aria-label="文档编辑器"] h3, [aria-label="文档编辑器"] h4, [aria-label="文档编辑器"] h5, [aria-label="文档编辑器"] h6`);
  const heading = headings[headingIndex];
  if (!heading) {
    return false;
  }
  heading.scrollIntoView({ behavior: "smooth", block: "start" });
  return true;
}
