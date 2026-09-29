import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { NotificationItem } from "../api/console-api";

import { NotificationList } from "./notification-list";

const consoleApi = vi.hoisted(() => ({
  markNotificationRead: vi.fn(),
}));
vi.mock("@/features/console/api/console-api", () => consoleApi);

function LocationProbe() {
  const { pathname } = useLocation();
  return <div data-testid="location">{pathname}</div>;
}

function renderList(items: NotificationItem[], onClose: () => void) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/overview"]}>
        <Routes>
          <Route path="/overview" element={<NotificationList items={items} onClose={onClose} />} />
          <Route path="*" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const notification = (overrides: Partial<NotificationItem>): NotificationItem => ({
  id: 1,
  type: "APPROVAL_REQUESTED",
  title: "待审批请求",
  body: "有一个 HIGH 风险工具调用待审批",
  resourceType: "APPROVAL",
  resourceId: "9",
  readAt: null,
  createdAt: "2026-09-29T08:00:00Z",
  ...overrides,
});

describe("NotificationList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    consoleApi.markNotificationRead.mockResolvedValue(undefined);
  });
  afterEach(cleanup);

  it("点击未读通知先标记已读再跳转并关闭", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    renderList([notification({ id: 11, type: "APPROVAL_REQUESTED", title: "待审批请求", resourceType: "APPROVAL", resourceId: "9" })], onClose);

    await user.click(screen.getByRole("button", { name: /待审批请求/ }));

    expect(consoleApi.markNotificationRead).toHaveBeenCalledWith(11);
    await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent("/approvals"));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("点击已读通知不重复标记但仍跳转", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    renderList([notification({ id: 12, type: "WORK_ITEM_ASSIGNED", title: "新任务指派", resourceType: "REQUIREMENT", resourceId: "5", readAt: "2026-09-29T09:00:00Z" })], onClose);

    await user.click(screen.getByRole("button", { name: /新任务指派/ }));

    expect(consoleApi.markNotificationRead).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent("/requirements/5"));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("无可跳转目标时只标记已读且不抛错", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    renderList([notification({ id: 13, type: "REVIEW_REQUESTED", title: "评审待办", resourceType: null, resourceId: null })], onClose);

    await user.click(screen.getByRole("button", { name: /评审待办/ }));

    expect(consoleApi.markNotificationRead).toHaveBeenCalledWith(13);
    await waitFor(() => expect(onClose).toHaveBeenCalledOnce());
    expect(screen.queryByTestId("location")).not.toBeInTheDocument();
  });
});
