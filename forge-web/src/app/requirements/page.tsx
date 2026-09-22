import { ProtectedApp } from "@/features/auth";
import { RequirementListPage } from "@/features/work-item";

export default function RequirementsPage() {
  return (
    <ProtectedApp>
      <RequirementListPage />
    </ProtectedApp>
  );
}
