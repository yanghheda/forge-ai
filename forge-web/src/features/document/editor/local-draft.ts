import type { JSONContent } from "@tiptap/core";

export type ProseMirrorDocument = JSONContent & { type: "doc" };

export function draftKey(userId: number, documentId: number, baseVersion: number): string {
  return `forge:document-draft:${userId}:${documentId}:${baseVersion}`;
}

export function saveDraft(userId: number, documentId: number, baseVersion: number, content: ProseMirrorDocument): void {
  localStorage.setItem(draftKey(userId, documentId, baseVersion), JSON.stringify(content));
}

export function loadDraft(userId: number, documentId: number, baseVersion: number): ProseMirrorDocument | null {
  const saved = localStorage.getItem(draftKey(userId, documentId, baseVersion));
  if (!saved) {
    return null;
  }
  try {
    return JSON.parse(saved) as ProseMirrorDocument;
  } catch {
    localStorage.removeItem(draftKey(userId, documentId, baseVersion));
    return null;
  }
}

export function clearDraft(userId: number, documentId: number, baseVersion: number): void {
  localStorage.removeItem(draftKey(userId, documentId, baseVersion));
}
