import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

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
  view: { dom: document.createElement("div") },
};
let editorOptions: { editable?: boolean; onCreate?: (event: { editor: typeof editor }) => void; onUpdate?: (event: { editor: typeof editor }) => void } = {};

vi.mock("@tiptap/react", () => ({
  EditorContent: () => <div data-testid="editor-content" />,
  useEditor: (options: typeof editorOptions) => {
    editorOptions = options;
    return editor;
  },
}));

import { DocumentEditor } from "./document-editor";

describe("DocumentEditor", () => {
  afterEach(cleanup);

  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    editorOptions = {};
  });

  it("编辑后可从顶部工具栏保存新版本", async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<DocumentEditor userId={1} documentId={2} baseVersion={3} serverContent={{ type: "doc" }} onSave={onSave} />);

    expect(screen.getByTestId("editor-content")).toBeInTheDocument();
    const toolbar = screen.getByLabelText("文档格式工具栏");
    const saveButton = within(toolbar).getByRole("button", { name: "保存为新版本" });
    expect(saveButton).toBeDisabled();

    act(() => editorOptions.onUpdate?.({ editor }));
    expect(saveButton).toBeEnabled();
    fireEvent.click(saveButton);
    await waitFor(() => expect(onSave).toHaveBeenCalledWith(editor.getJSON()));
    expect(screen.getByText(/已保存于/)).toBeInTheDocument();
  });

  it("输入内容时不会把草稿重新灌入编辑器导致光标跳动", () => {
    render(<DocumentEditor userId={1} documentId={2} baseVersion={3} serverContent={{ type: "doc" }} onSave={vi.fn()} />);

    act(() => editorOptions.onUpdate?.({ editor }));

    expect(editor.commands.setContent).not.toHaveBeenCalled();
    expect(localStorage.getItem("forge:document-draft:1:2:3")).toBe(JSON.stringify(editor.getJSON()));
  });

  it("同步目录时携带标题在正文中的位置", () => {
    const onOutlineChange = vi.fn();
    editor.getJSON = () => ({
      type: "doc",
      content: [
        { type: "paragraph" },
        { type: "heading", attrs: { level: 2 }, content: [{ type: "text", text: "用户场景" }] },
        { type: "heading", attrs: { level: 3 }, content: [{ type: "text", text: "   " }] },
        { type: "heading", attrs: { level: 3 }, content: [{ type: "text", text: "权限矩阵" }] },
      ],
    });

    render(<DocumentEditor userId={1} documentId={2} baseVersion={3} serverContent={{ type: "doc" }} onSave={vi.fn()} onOutlineChange={onOutlineChange} />);
    editorOptions.onCreate?.({ editor });

    expect(onOutlineChange).toHaveBeenCalledWith([
      { headingIndex: 0, level: 2, title: "用户场景" },
      { headingIndex: 2, level: 3, title: "权限矩阵" },
    ]);
  });

  it("只读模式下禁用正文编辑、隐藏保存并把编辑器设为不可编辑", () => {
    render(<DocumentEditor userId={1} documentId={2} baseVersion={3} serverContent={{ type: "doc" }} onSave={vi.fn()} readOnly />);

    expect(editorOptions).toMatchObject({ editable: false });
    expect(screen.queryByRole("button", { name: "保存为新版本" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "粗体" })).toBeDisabled();
    expect(screen.getByText("只读")).toBeInTheDocument();
  });
});
