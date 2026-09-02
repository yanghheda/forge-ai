"use client";

import { useQuery } from "@tanstack/react-query";

import {
  loadProjectOverview,
  type ProjectOverviewSummary,
} from "../api/project-overview-api";

export const projectKeys = {
  overview: (workspace: string, project: string) =>
    ["workspaces", workspace, "projects", project, "overview"] as const,
};

export function useProjectOverview(
  workspace: string,
  project: string,
  queryFunction: () => Promise<ProjectOverviewSummary | null> = () => loadProjectOverview(),
) {
  return useQuery({
    queryKey: projectKeys.overview(workspace, project),
    queryFn: () => queryFunction(),
  });
}
