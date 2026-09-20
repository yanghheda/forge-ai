import { ProtectedApp } from "@/features/auth";
import { RequirementAnalyticsDashboard } from "@/features/work-item";

export default function OverviewPage() {
  return (
    <ProtectedApp>
      <RequirementAnalyticsDashboard />
    </ProtectedApp>
  );
}
