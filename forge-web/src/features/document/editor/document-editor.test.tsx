import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const run = vi.fn();
const chain = {
  focus: () => chain,
  setParagraph: () => chain,
  toggleHeading: () => chain,
  toggleBold: () => chain,
  toggleItalic: () => chain,
  toggleStrike: () => chain,
  toggleBlockquote: () => chain,
  toggleCodeBlock: () => chain,
  toggleBulletList: () => chain,
  toggleOrderedList: () => chain,
  undo: () => chain,
  redo: () => chain,
  run,
};
const editor = {
  chain: () => chain,
  commands: { setContent: vi.fn() },
  getJSON: () => ({ type: "doc", content: [{ type: "paragraph" }] }),
  isActive: () => false,
  setEditable: vi.fn(),
};
let editorOptions: { onUpdate?: (event: { editor: typeof editor }) => void } = {};

vi.mock("@tiptap/react", () => ({
  EditorContent: () => <div data-testid="editor-content" />,
  useEditor: (options: typeof editorOptions) => {
    editorOptions = options;
    return editor;
  },
}));

import { DocumentEditor } from "./document-editor";

describe("DocumentEditor", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    editorOptions = {};
  });

  it("进入页面即可编辑，并能把内容保存为新版本", async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<DocumentEditor userId={1} documentId={2} baseVersion={3} serverContent={{ type: "doc" }} onSave={onSave} />);

    expect(screen.getByTestId("editor-content")).toBeInTheDocument();
    expect(screen.getByLabelText("文档格式工具栏")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "保存为新版本" }));
    await waitFor(() => expect(onSave).toHaveBeenCalledWith(editor.getJSON()));
    expect(screen.getByText(/已保存于/)).toBeInTheDocument();
  });

  it("输入内容时不会把草稿重新灌入编辑器导致光标跳动", () => {
    render(<DocumentEditor userId={1} documentId={2} baseVersion={3} serverContent={{ type: "doc" }} onSave={vi.fn()} />);

    editorOptions.onUpdate?.({ editor });

    expect(editor.commands.setContent).not.toHaveBeenCalled();
    expect(localStorage.getItem("forge:document-draft:1:2:3")).toBe(JSON.stringify(editor.getJSON()));
  });
});
