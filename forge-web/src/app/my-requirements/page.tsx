import { ProtectedApp } from "@/features/auth";
import { RequirementDashboard } from "@/features/work-item";

export default function MyRequirementsPage() {
  return (
    <ProtectedApp>
      <RequirementDashboard mine />
    </ProtectedApp>
  );
}
