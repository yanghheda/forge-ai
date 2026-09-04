import { GitLabSettings } from "@/features/gitlab";

export default async function GitLabSettingsPage({ params }: { params: Promise<{ workspace: string }> }) {
  const { workspace } = await params;
  return <GitLabSettings workspaceSlug={workspace} />;
}
