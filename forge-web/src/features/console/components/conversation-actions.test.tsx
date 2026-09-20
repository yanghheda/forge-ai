import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { ConversationActions } from "./conversation-actions";

afterEach(cleanup);

it("点击更多后可以重命名，空标题不能提交", async () => {
  const rename = vi.fn().mockResolvedValue(undefined);
  const user = userEvent.setup();
  render(<ConversationActions title="原标题" onRename={rename} onDelete={vi.fn()} />);
  expect(screen.queryByText("重命名")).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "更多操作：原标题" }));
  await waitFor(async () => await user.click(screen.getByText("重命名")));
  const input = screen.getByRole("textbox", { name: "会话标题" });
  fireEvent.change(input, { target: { value: "   " } });
  expect(screen.getByRole("button", { name: "保存" })).toBeDisabled();
  fireEvent.change(input, { target: { value: " 新标题 " } });
  await user.click(screen.getByRole("button", { name: "保存" }));
  await waitFor(() => expect(rename).toHaveBeenCalledWith("新标题"));
});

it("删除会话需要二次确认", async () => {
  const remove = vi.fn().mockResolvedValue(undefined);
  const user = userEvent.setup();
  render(<ConversationActions title="原标题" onRename={vi.fn()} onDelete={remove} />);
  await user.click(screen.getByRole("button", { name: "更多操作：原标题" }));
  await waitFor(async () => await user.click(screen.getByText("删除")));
  expect(remove).not.toHaveBeenCalled();
  await user.click(screen.getByRole("button", { name: "删除" }));
  await waitFor(() => expect(remove).toHaveBeenCalledOnce());
});
