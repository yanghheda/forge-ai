import { cleanup, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";

import { DeliveryScreen } from "./delivery-screen";

const wrapper = ({ children }: { children: React.ReactNode }) => <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;

describe("DeliveryScreen", () => {
  afterEach(cleanup);

  it("展示需求上下文导航与任务看板", () => {
    render(<DeliveryScreen kind="tasks" requirementId="1" />, { wrapper });
    expect(screen.getByRole("navigation", { name: "需求交付阶段" })).toBeInTheDocument();
    expect(screen.getByText("待处理")).toBeInTheDocument();
    expect(screen.getByText("进行中")).toBeInTheDocument();
    expect(screen.getByText("已完成")).toBeInTheDocument();
  });

  it("新建任务使用抽屉承载表单", async () => {
    render(<DeliveryScreen kind="tasks" requirementId="1" />, { wrapper });
    await userEvent.click(screen.getByRole("button", { name: "新建任务" }));
    expect(screen.getByRole("dialog", { name: "新建任务" })).toBeInTheDocument();
  });
});
