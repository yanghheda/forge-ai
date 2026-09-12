"use client";

import StarterKit from "@tiptap/starter-kit";
import { EditorContent, useEditor } from "@tiptap/react";
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
}

export function DocumentEditor({ userId, documentId, baseVersion, serverContent, onSave }: DocumentEditorProps) {
  const [draft, setDraft] = useState(() => loadDraft(userId, documentId, baseVersion));
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [savedAt, setSavedAt] = useState<Date | null>(null);
  const editor = useEditor({
    extensions: [StarterKit],
    content: draft ?? serverContent,
    editable: true,
    onUpdate: ({ editor: instance }) => {
      const content = instance.getJSON() as ProseMirrorDocument;
      setDirty(true);
      setDraft(content);
      saveDraft(userId, documentId, baseVersion, content);
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
    <section aria-label="文档编辑器" className={styles.editor}>
      {draft && (
        <p className={styles.draftAlert} role="alert">
          检测到本地草稿，请确认内容后再保存为新版本。
        </p>
      )}
      <div className={styles.toolbar} aria-label="文档格式工具栏">
        <div className={styles.toolGroup}>
          <button type="button" aria-label="正文" aria-pressed={editor.isActive("paragraph")} onClick={() => editor.chain().focus().setParagraph().run()}>
            正文
          </button>
          <button type="button" aria-label="二级标题" aria-pressed={editor.isActive("heading", { level: 2 })} onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}>
            H2
          </button>
          <button type="button" aria-label="一级标题" aria-pressed={editor.isActive("heading", { level: 1 })} onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}>
            H1
          </button>
        </div>
        <span className={styles.separator} />
        <div className={styles.toolGroup}>
          <button type="button" aria-label="粗体" aria-pressed={editor.isActive("bold")} onClick={() => editor.chain().focus().toggleBold().run()}>
            <strong>B</strong>
          </button>
          <button type="button" aria-label="斜体" aria-pressed={editor.isActive("italic")} onClick={() => editor.chain().focus().toggleItalic().run()}>
            <em>I</em>
          </button>
          <button type="button" aria-label="删除线" aria-pressed={editor.isActive("strike")} onClick={() => editor.chain().focus().toggleStrike().run()}>
            <s>S</s>
          </button>
          <button type="button" aria-label="无序列表" aria-pressed={editor.isActive("bulletList")} onClick={() => editor.chain().focus().toggleBulletList().run()}>
            • 列表
          </button>
          <button type="button" aria-label="有序列表" aria-pressed={editor.isActive("orderedList")} onClick={() => editor.chain().focus().toggleOrderedList().run()}>
            1. 列表
          </button>
          <button type="button" aria-label="引用" aria-pressed={editor.isActive("blockquote")} onClick={() => editor.chain().focus().toggleBlockquote().run()}>
            “ ”
          </button>
          <button type="button" aria-label="代码块" aria-pressed={editor.isActive("codeBlock")} onClick={() => editor.chain().focus().toggleCodeBlock().run()}>
            {"</>"}
          </button>
        </div>
        <span className={styles.separator} />
        <div className={styles.toolGroup}>
          <button type="button" aria-label="撤销" onClick={() => editor.chain().focus().undo().run()}>
            ↶
          </button>
          <button type="button" aria-label="重做" onClick={() => editor.chain().focus().redo().run()}>
            ↷
          </button>
        </div>
      </div>
      <div className={styles.paper}>
        <EditorContent editor={editor} />
      </div>
      <footer className={styles.footer}>
        <span className={dirty ? styles.unsaved : styles.saved}>{dirty ? "有未保存的修改" : savedAt ? `已保存于 ${savedAt.toLocaleTimeString()}` : "自动保存本地草稿"}</span>
        <button className={styles.saveButton} type="button" disabled={saving} onClick={() => void save()}>
          {saving ? "正在保存…" : "保存为新版本"}
        </button>
      </footer>
    </section>
  );
}
