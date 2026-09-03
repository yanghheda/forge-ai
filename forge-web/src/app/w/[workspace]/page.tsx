import { WorkspaceProjects } from "@/features/project";

export default async function WorkspaceEntryPage({ params }: { params: Promise<{ workspace: string }> }) {
  const { workspace } = await params;
  return <WorkspaceProjects workspaceSlug={workspace} />;
}
