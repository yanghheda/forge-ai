import { WorkspaceMembers } from "@/features/workspace";

export default async function WorkspaceMembersPage({ params }: { params: Promise<{ workspace: string }> }) {
  const { workspace } = await params;
  return <WorkspaceMembers workspaceSlug={workspace} />;
}
