"use client";

import { useProjectOverview } from "../hooks/use-project-overview";
import { ProjectOverviewView } from "./project-overview-view";

export interface ProjectOverviewProps {
  project: string;
  workspace: string;
}

export function ProjectOverview({ project, workspace }: ProjectOverviewProps) {
  const overview = useProjectOverview(workspace, project);

  return (
    <ProjectOverviewView
      data={overview.data}
      error={overview.error}
      loading={overview.isPending}
      project={project}
      workspace={workspace}
    />
  );
}
