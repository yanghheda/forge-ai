import { ProtectedApp } from "@/features/auth";
import { DefectDetail } from "@/features/work-item";

export function DefectDetailView() {
  return (
    <ProtectedApp>
      <DefectDetail />
    </ProtectedApp>
  );
}
