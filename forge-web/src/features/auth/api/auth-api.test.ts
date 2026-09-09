import { describe, expect, it, vi } from "vitest";

import { getCurrentUser, getSetupStatus, initializeInstance, login, logout, register } from "./auth-api";

describe("auth api", () => {
  it("所有身份写操作都声明非安全 HTTP 方法以触发 CSRF transport", async () => {
    const client = { request: vi.fn().mockResolvedValue(undefined) };

    await getSetupStatus(client);
    await initializeInstance({
      adminEmail: "owner@example.com", adminDisplayName: "Owner", password: "correct-horse-42",
      organizationName: "Forge", organizationSlug: "forge",
    }, client);
    await register({ email: "dev@example.com", displayName: "Dev", password: "member-password-42", role: "DEVELOPER" }, client);
    await login({ email: "owner@example.com", password: "correct-horse-42" }, client);
    await getCurrentUser(client);
    await logout(client);

    expect(client.request.mock.calls.map(([path, init]) => [path, init?.method])).toEqual([
      ["/v1/setup/status", undefined], ["/v1/setup/initialize", "POST"], ["/v1/auth/register", "POST"], ["/v1/auth/login", "POST"],
      ["/v1/me", undefined], ["/v1/auth/logout", "POST"],
    ]);
  });
});
