import type { DocumentVersion } from "@/features/document";
import { guardHintLabel } from "@/lib/labels";

export const uxChecklistItems = ["userFlow", "pageList", "keyInteraction", "exceptionState"] as const;
export type UxChecklist = Record<(typeof uxChecklistItems)[number], boolean>;

export function selectEditableVersion(versions: DocumentVersion[] | undefined) {
  return versions?.[0];
}

export function unresolvedUxReviewHints(hints: string[] | undefined, checklist: UxChecklist) {
  return (hints ?? []).filter((hint) => !uxChecklistItems.includes(hint as keyof UxChecklist) || !checklist[hint as keyof UxChecklist]);
}

export function uxChecklistLabel(item: keyof UxChecklist) {
  return guardHintLabel(item);
}
