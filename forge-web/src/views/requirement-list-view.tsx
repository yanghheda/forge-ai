import { ProtectedApp } from "@/features/auth";
import { RequirementListPage } from "@/features/work-item";

export function RequirementListView() {
  return (
    <ProtectedApp>
      <RequirementListPage />
    </ProtectedApp>
  );
}
