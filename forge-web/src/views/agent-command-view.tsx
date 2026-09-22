import { ProtectedApp } from "@/features/auth";
import { AgentCommandScreen } from "@/features/console";

export function AgentCommandView() {
  return (
    <ProtectedApp>
      <AgentCommandScreen />
    </ProtectedApp>
  );
}
