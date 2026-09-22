import { ProtectedApp } from "@/features/auth";
import { RequirementAnalyticsDashboard } from "@/features/work-item";

export function OverviewView() {
  return (
    <ProtectedApp>
      <RequirementAnalyticsDashboard />
    </ProtectedApp>
  );
}
