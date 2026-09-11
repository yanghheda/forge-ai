import { ProtectedApp } from "@/features/auth";
import { DeliveryScreen } from "@/features/console";

export default async function DocumentPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <ProtectedApp>
      <DeliveryScreen kind="document" requirementId={(await params).id} />
    </ProtectedApp>
  );
}
