import { apiClient } from "@/lib/api";

interface RequestClient {
  request<T = unknown>(path: string, init?: RequestInit): Promise<T>;
}

export interface ProjectOverviewSummary {
  serviceName: string;
  serviceStatus: string;
}

function readString(record: Record<string, unknown>, key: string): string | undefined {
  const value = record[key];
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

export async function loadProjectOverview(
  client: RequestClient = apiClient,
): Promise<ProjectOverviewSummary | null> {
  const response = await client.request("/v1/system/status");
  if (typeof response !== "object" || response === null) {
    return null;
  }

  const serviceName = readString(response as Record<string, unknown>, "application");
  const serviceStatus = readString(response as Record<string, unknown>, "status");
  return serviceName && serviceStatus ? { serviceName, serviceStatus } : null;
}
