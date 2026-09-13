import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { UxWorkspaceView, type UxWorkspaceModel } from "./ux-delivery";

const model: UxWorkspaceModel = {
  requirement: { itemKey: "DEMO-1", title: "登录体验", status: "UX_REVIEW", updatedAt: "2026-09-07T06:20:00Z" },
  task: { title: "UX: 登录体验", status: "IN_REVIEW", assigneeUserId: 8, createdAt: "2026-09-05T02:02:00Z", updatedAt: "2026-09-07T06:20:00Z" },
  documents: [{ id: 3, type: "UX_SPEC", title: "登录 UX Spec", status: "PUBLISHED", currentVersionId: 12, version: 2 }],
  versions: [{ id: 12, documentId: 3, versionNo: 2, createdAt: "2026-09-07T06:20:00Z", plainText: "用户流 页面清单 关键交互 异常状态", contentHash: "hash", content: { type: "doc", content: [] } }],
  activity: [{ kind: "EVENT" as const, id: 9, actorId: 7, action: "SUBMIT_UX_REVIEW" as const, reason: null, body: null, createdAt: "2026-09-07T03:20:00Z" }],
  workflow: { version: 4, availableActions: ["APPROVE_UX_REVIEW" as const, "REJECT_UX_REVIEW" as const], guardHints: {} },
  uxDesigner: "李UX",
  reviewer: "张产品",
};

describe("UxWorkspaceView", () => {
  afterEach(cleanup);
  it("按视觉稿三栏展示真实 UX 交付数据", () => {
    const onEdit = vi.fn();
    render(<UxWorkspaceView model={model} checklist={{ userFlow: true, pageList: true, keyInteraction: true, exceptionState: true }} busy={false} reason="" onChecklistChange={vi.fn()} onReasonChange={vi.fn()} onAction={vi.fn()} onGenerate={vi.fn()} onEdit={onEdit} />);

    expect(screen.getByRole("heading", { name: "UX 工作区" })).toBeInTheDocument();
    expect(screen.getByText("登录 UX Spec")).toBeInTheDocument();
    expect(screen.getByText("设计检查点")).toBeInTheDocument();
    expect(screen.getByText("评审结论")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "通过 UX 评审" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "编辑 UX Spec" })).toBeInTheDocument();
  });

  it("退回评审必须填写原因", async () => {
    const onAction = vi.fn();
    render(<UxWorkspaceView model={model} checklist={{ userFlow: true, pageList: true, keyInteraction: true, exceptionState: true }} busy={false} reason="" onChecklistChange={vi.fn()} onReasonChange={vi.fn()} onAction={onAction} onGenerate={vi.fn()} onEdit={vi.fn()} />);

    const reject = screen.getByRole("button", { name: "退回 UX 评审" });
    expect(reject).toBeDisabled();
    await userEvent.click(reject);
    expect(onAction).not.toHaveBeenCalled();
  });
});
