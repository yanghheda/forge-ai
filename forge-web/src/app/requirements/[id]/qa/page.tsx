import { ProtectedApp } from "@/features/auth";
import { DeliveryScreen } from "@/features/console";

export default async function QaPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <ProtectedApp>
      <DeliveryScreen kind="qa" requirementId={(await params).id} />
    </ProtectedApp>
  );
}
