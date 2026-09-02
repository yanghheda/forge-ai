import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import { useProjectOverview } from "./use-project-overview";

describe("useProjectOverview", () => {
  it("通过 Feature API 获取数据并放入 Query cache", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const queryFunction = vi.fn().mockResolvedValue({
      serviceName: "forge-server",
      serviceStatus: "UP",
    });
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );

    const { result } = renderHook(
      () => useProjectOverview("forge", "demo", queryFunction),
      { wrapper },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(queryFunction).toHaveBeenCalledOnce();
    expect(result.current.data).toEqual({ serviceName: "forge-server", serviceStatus: "UP" });
  });
});
