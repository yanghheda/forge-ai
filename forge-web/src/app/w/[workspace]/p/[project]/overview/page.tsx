import { notFound } from "next/navigation";

import { ProjectDetail, projectRouteSchema } from "@/features/project";

interface ProjectOverviewPageProps {
  params: Promise<{ workspace: string; project: string }>;
}

export default async function ProjectOverviewPage({ params }: ProjectOverviewPageProps) {
  const route = projectRouteSchema.safeParse(await params);
  if (!route.success) {
    notFound();
  }

  return <ProjectDetail workspaceSlug={route.data.workspace} projectKey={route.data.project} />;
}
