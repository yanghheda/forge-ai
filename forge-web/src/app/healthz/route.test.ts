import { describe, expect, it } from "vitest";

import { GET } from "./route";

describe("forge-web healthz", () => {
  it("returns the application liveness status", async () => {
    const response = GET();

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({ application: "forge-web", status: "UP" });
  });
});
