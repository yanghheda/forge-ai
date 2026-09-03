"use client";

import StarterKit from "@tiptap/starter-kit";
import { EditorContent, useEditor } from "@tiptap/react";
import { useState } from "react";
import type { ProseMirrorDocument } from "./local-draft";
import { loadDraft, saveDraft } from "./local-draft";

interface DocumentEditorProps {
  userId: number;
  documentId: number;
  baseVersion: number;
  serverContent: ProseMirrorDocument;
  onSave(content: ProseMirrorDocument): Promise<void>;
}

export function DocumentEditor({ userId, documentId, baseVersion, serverContent, onSave }: DocumentEditorProps) {
  const [localDraft] = useState(() => loadDraft(userId, documentId, baseVersion));
  const hasServerChange = localDraft !== null;
  const editor = useEditor({
    extensions: [StarterKit],
    content: localDraft ?? serverContent,
    onUpdate: ({ editor: instance }) =>
      saveDraft(userId, documentId, baseVersion, instance.getJSON() as ProseMirrorDocument),
  });
  if (!editor) {
    return null;
  }
  return (
    <section aria-label="Document editor">
      {hasServerChange && <p role="alert">服务端版本已变化；本地草稿不会自动覆盖。请复制草稿、查看差异或丢弃草稿。</p>}
      <EditorContent editor={editor} />
      <button type="button" onClick={() => void onSave(editor.getJSON() as ProseMirrorDocument)}>
        保存新版本
      </button>
    </section>
  );
}
