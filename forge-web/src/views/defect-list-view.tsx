import { ProtectedApp } from "@/features/auth";
import { DefectList } from "@/features/work-item";

export function DefectListView() {
  return (
    <ProtectedApp>
      <DefectList />
    </ProtectedApp>
  );
}
