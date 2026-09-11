"use client";

import StarterKit from "@tiptap/starter-kit";
import { EditorContent, useEditor } from "@tiptap/react";
import { useEffect, useState } from "react";
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
  const [editing, setEditing] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [savedAt, setSavedAt] = useState<Date | null>(null);
  const editor = useEditor({
    extensions: [StarterKit],
    content: serverContent,
    editable: false,
    onUpdate: ({ editor: instance }) => {
      const content = instance.getJSON() as ProseMirrorDocument;
      setDirty(true);
      setDraft(content);
      saveDraft(userId, documentId, baseVersion, content);
    },
  });
  useEffect(() => {
    if (!editor) return;
    editor.setEditable(editing);
    if (!editing) {
      editor.commands.setContent(serverContent, { emitUpdate: false });
    }
  }, [editing, editor, serverContent]);
  if (!editor) {
    return null;
  }
  const beginEditing = () => {
    if (draft) {
      editor.commands.setContent(draft, { emitUpdate: false });
    }
    setEditing(true);
  };
  const save = async () => {
    setSaving(true);
    try {
      await onSave(editor.getJSON() as ProseMirrorDocument);
      setDirty(false);
      setDraft(null);
      clearDraft(userId, documentId, baseVersion);
      setSavedAt(new Date());
      setEditing(false);
    } finally {
      setSaving(false);
    }
  };
  return (
    <section aria-label="文档编辑器" className={styles.editor}>
      {editing && draft && (
        <p className={styles.draftAlert} role="alert">
          检测到本地草稿，请确认内容后再保存为新版本。
        </p>
      )}
      {editing && (
        <div className={styles.toolbar} aria-label="文档格式工具栏">
          <div className={styles.toolGroup}>
            <button type="button" aria-label="正文" aria-pressed={editor.isActive("paragraph")} onClick={() => editor.chain().focus().setParagraph().run()}>
              正文
            </button>
            <button type="button" aria-label="二级标题" aria-pressed={editor.isActive("heading", { level: 2 })} onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}>
              H2
            </button>
          </div>
          <span className={styles.separator} />
          <div className={styles.toolGroup}>
            <button type="button" aria-label="粗体" aria-pressed={editor.isActive("bold")} onClick={() => editor.chain().focus().toggleBold().run()}>
              <strong>B</strong>
            </button>
            <button type="button" aria-label="无序列表" aria-pressed={editor.isActive("bulletList")} onClick={() => editor.chain().focus().toggleBulletList().run()}>
              • 列表
            </button>
            <button type="button" aria-label="有序列表" aria-pressed={editor.isActive("orderedList")} onClick={() => editor.chain().focus().toggleOrderedList().run()}>
              1. 列表
            </button>
          </div>
        </div>
      )}
      <div className={styles.paper}>
        <EditorContent editor={editor} />
      </div>
      <footer className={styles.footer}>
        <span className={dirty ? styles.unsaved : styles.saved}>{editing && dirty ? "有未保存的修改" : savedAt ? `已保存于 ${savedAt.toLocaleTimeString()}` : "阅读模式"}</span>
        {editing ? (
          <button className={styles.saveButton} type="button" disabled={saving} onClick={() => void save()}>
            {saving ? "正在保存…" : "保存为新版本"}
          </button>
        ) : (
          <button className={styles.saveButton} type="button" onClick={beginEditing}>
            编辑文档
          </button>
        )}
      </footer>
    </section>
  );
}
