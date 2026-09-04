import { notFound } from "next/navigation";
import { projectRouteSchema } from "@/features/project";
import { UxWorkspace } from "@/features/work-item";

interface UxWorkspacePageProps {
  params: Promise<{ workspace: string; project: string }>;
}

export default async function UxWorkspacePage({ params }: UxWorkspacePageProps) {
  const route = projectRouteSchema.safeParse(await params);
  if (!route.success) notFound();
  return <UxWorkspace workspaceSlug={route.data.workspace} projectKey={route.data.project} />;
}
