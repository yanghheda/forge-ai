import { notFound } from "next/navigation";

import { ProjectOverview, projectRouteSchema } from "@/features/project";

interface ProjectOverviewPageProps {
  params: Promise<{ workspace: string; project: string }>;
}

export default async function ProjectOverviewPage({ params }: ProjectOverviewPageProps) {
  const route = projectRouteSchema.safeParse(await params);
  if (!route.success) {
    notFound();
  }

  return <ProjectOverview workspace={route.data.workspace} project={route.data.project} />;
}
