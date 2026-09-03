import { describe, expect, it } from "vitest";
import { draftKey, loadDraft, saveDraft, type ProseMirrorDocument } from "./local-draft";

describe("document local drafts", () => {
  it("isolates a draft by user, document, and base server version", () => {
    const content: ProseMirrorDocument = { type: "doc", content: [] };
    saveDraft(7, 11, 3, content);

    expect(draftKey(7, 11, 3)).toBe("forge:document-draft:7:11:3");
    expect(loadDraft(7, 11, 3)).toEqual(content);
    expect(loadDraft(7, 11, 4)).toBeNull();
  });
});
