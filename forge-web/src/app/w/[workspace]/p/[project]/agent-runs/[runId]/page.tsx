import { notFound } from "next/navigation";
import { AgentRunRoute } from "@/features/agent-run";

export default async function Page({
  params,
}: {
  params: Promise<{ workspace: string; project: string; runId: string }>;
}) {
  const route = await params;
  if (!/^[0-9A-HJKMNP-TV-Z]{26}$/.test(route.runId)) notFound();
  return (
    <AgentRunRoute
      workspaceSlug={route.workspace}
      projectKey={route.project}
      runId={route.runId}
    />
  );
}
