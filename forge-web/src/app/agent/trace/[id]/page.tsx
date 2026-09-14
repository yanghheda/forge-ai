import { ProtectedApp } from "@/features/auth";
import { AgentRunRoute } from "@/features/agent-run";

export default async function AgentTracePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <ProtectedApp>
      <AgentRunRoute runId={id} />
    </ProtectedApp>
  );
}
