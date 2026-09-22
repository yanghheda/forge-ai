import { useParams } from "react-router";

import { ProtectedApp } from "@/features/auth";
import { AgentRunRoute } from "@/features/agent-run";

export function AgentTraceView() {
  const { id } = useParams();
  return (
    <ProtectedApp>
      <AgentRunRoute runId={id ?? ""} />
    </ProtectedApp>
  );
}
