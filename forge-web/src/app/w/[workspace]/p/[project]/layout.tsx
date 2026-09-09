import type { ReactNode } from "react";

import { ProjectAccessGuard } from "@/features/project";

export default async function ProjectLayout({
  children,
  params,
}: {
  children: ReactNode;
  params: Promise<{ workspace: string; project: string }>;
}) {
  const { workspace, project } = await params;
  return (
    <ProjectAccessGuard workspaceSlug={workspace} projectKey={project}>
      {children}
    </ProjectAccessGuard>
  );
}
