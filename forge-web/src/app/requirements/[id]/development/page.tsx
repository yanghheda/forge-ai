import { ProtectedApp } from "@/features/auth";
import { DeliveryScreen } from "@/features/console";

export default async function DevelopmentPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <ProtectedApp>
      <DeliveryScreen kind="development" requirementId={(await params).id} />
    </ProtectedApp>
  );
}
