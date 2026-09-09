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
  commands: { setContent: vi.fn() },
  getJSON: () => ({ type: "doc", content: [{ type: "paragraph" }] }),
  isActive: () => false,
  setEditable: vi.fn(),
};

vi.mock("@tiptap/react", () => ({
  EditorContent: () => <div data-testid="editor-content" />,
  useEditor: () => editor,
}));

import { DocumentEditor } from "./document-editor";

describe("DocumentEditor", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it("默认以只读方式展示内容，点击编辑后才显示格式工具栏和保存入口", async () => {
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

    expect(screen.getByTestId("editor-content")).toBeInTheDocument();
    expect(screen.queryByLabelText("文档格式工具栏")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "保存为新版本" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "编辑文档" }));
    expect(screen.getByLabelText("文档格式工具栏")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "保存为新版本" }));
    await waitFor(() => expect(onSave).toHaveBeenCalledWith(editor.getJSON()));
    expect(screen.getByRole("button", { name: "编辑文档" })).toBeInTheDocument();
  });
});
