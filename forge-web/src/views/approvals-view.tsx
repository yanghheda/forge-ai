import { PendingApprovals } from "@/features/agent-run";
import { ProtectedApp } from "@/features/auth";

export function ApprovalsView() {
  return (
    <ProtectedApp>
      <PendingApprovals />
    </ProtectedApp>
  );
}
