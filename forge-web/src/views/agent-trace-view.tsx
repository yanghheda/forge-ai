import { AgentTraceWorkspace } from "@/features/agent-run";
import { ProtectedApp } from "@/features/auth";

export function AgentTraceView() {
  return (
    <ProtectedApp>
      <AgentTraceWorkspace />
    </ProtectedApp>
  );
}
