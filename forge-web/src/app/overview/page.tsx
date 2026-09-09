import { ProtectedApp } from "@/features/auth";
import { RequirementDashboard } from "@/features/work-item";

export default function OverviewPage() {
  return <ProtectedApp><RequirementDashboard /></ProtectedApp>;
}
