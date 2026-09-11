import { ProtectedApp } from "@/features/auth";
import { AgentTraceScreen } from "@/features/console";

export default async function AgentTracePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <ProtectedApp><AgentTraceScreen runId={id} /></ProtectedApp>;
}
