import { render } from "@testing-library/react";
import { type QueryClient, useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { describe, expect, it } from "vitest";

import { AppProviders } from "./providers";

function QueryClientProbe({ report }: { report: (client: QueryClient) => void }) {
  const queryClient = useQueryClient();

  useEffect(() => {
    report(queryClient);
  }, [queryClient, report]);

  return null;
}

describe("AppProviders", () => {
  it("rerender 后继续使用同一个 QueryClient", () => {
    const clients: QueryClient[] = [];
    const report = (client: QueryClient) => clients.push(client);
    const view = render(
      <AppProviders>
        <QueryClientProbe report={report} />
      </AppProviders>,
    );

    view.rerender(
      <AppProviders>
        <QueryClientProbe report={report} />
      </AppProviders>,
    );

    expect(clients).toHaveLength(1);
    expect(clients[0]).toBeDefined();
  });
});
