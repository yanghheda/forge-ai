import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { selectEditableVersion, unresolvedUxReviewHints, UxReviewChecklist } from "./requirement-detail";

describe("UX review checklist", () => {
  it("在缺少已发布 UX Spec 时给出完成路径并允许确认交付项", () => {
    const onChange = vi.fn();
    render(
      <UxReviewChecklist
        checklist={{
          userFlow: false,
          pageList: false,
          keyInteraction: false,
          exceptionState: false,
        }}
        needsPublishedUxSpec
        onChange={onChange}
      />,
    );

    expect(screen.getByText(/请先在 UX Spec 页签创建文档、保存版本并发布/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("checkbox", { name: "用户流" }));
    expect(onChange).toHaveBeenCalledWith("userFlow", true);
    expect(screen.getAllByRole("checkbox")).toHaveLength(4);
  });
});

describe("document version selection", () => {
  const versions = [
    {
      id: 30,
      documentId: 2,
      versionNo: 3,
      content: { type: "doc" as const },
      plainText: "最新版本",
      contentHash: "hash-3",
      createdAt: "2026-09-07T00:00:00Z",
    },
    {
      id: 20,
      documentId: 2,
      versionNo: 2,
      content: { type: "doc" as const },
      plainText: "旧版本",
      contentHash: "hash-2",
      createdAt: "2026-09-06T00:00:00Z",
    },
  ];

  it("草稿 currentVersionId 缺失时仍选择最新历史版本用于发布", () => {
    expect(selectEditableVersion(versions)?.id).toBe(30);
  });

  it("已有发布版本时仍优先展示最新保存的草稿版本", () => {
    expect(selectEditableVersion(versions)?.id).toBe(30);
  });
});

describe("UX review guard hints", () => {
  it("勾选后的检查项不再显示为缺失，但保留未发布文档提示", () => {
    expect(unresolvedUxReviewHints(["publishedUxSpec", "userFlow", "pageList", "keyInteraction", "exceptionState"], { userFlow: true, pageList: true, keyInteraction: true, exceptionState: true })).toEqual(["publishedUxSpec"]);
  });
});
