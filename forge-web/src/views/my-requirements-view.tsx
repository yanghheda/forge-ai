import { ProtectedApp } from "@/features/auth";
import { MyRequirements } from "@/features/work-item";

export function MyRequirementsView() {
  return (
    <ProtectedApp>
      <MyRequirements />
    </ProtectedApp>
  );
}
