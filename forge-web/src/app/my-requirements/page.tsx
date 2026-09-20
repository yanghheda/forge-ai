import { ProtectedApp } from "@/features/auth";
import { MyRequirements } from "@/features/work-item";

export default function MyRequirementsPage() {
  return (
    <ProtectedApp>
      <MyRequirements />
    </ProtectedApp>
  );
}
