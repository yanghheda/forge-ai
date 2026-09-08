import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const run = vi.fn();
const chain = {
  focus: () => chain,
  setParagraph: () => chain,
  toggleHeading: () => chain,
  toggleBold: () => chain,
  toggleBulletList: () => chain,
  toggleOrderedList: () => chain,
  run,
};
const editor = {
  chain: () => chain,
  getJSON: () => ({ type: "doc", content: [{ type: "paragraph" }] }),
  isActive: () => false,
};

vi.mock("@tiptap/react", () => ({
  EditorContent: () => <div data-testid="editor-content" />,
  useEditor: () => editor,
}));

import { DocumentEditor } from "./document-editor";

describe("DocumentEditor", () => {
  beforeEach(() => localStorage.clear());

  it("提供格式工具栏、纸张内容区和版本保存入口", async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(
      <DocumentEditor
        userId={1}
        documentId={2}
        baseVersion={3}
        serverContent={{ type: "doc" }}
        onSave={onSave}
      />,
    );

    expect(screen.getByLabelText("文档格式工具栏")).toBeInTheDocument();
    expect(screen.getByTestId("editor-content")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "保存为新版本" }));
    await waitFor(() => expect(onSave).toHaveBeenCalledWith(editor.getJSON()));
  });
});
