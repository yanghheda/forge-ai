import { ProtectedApp } from "@/features/auth";
import { DeliveryScreen } from "@/features/console";

export default async function ReleasePage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <ProtectedApp>
      <DeliveryScreen kind="release" requirementId={(await params).id} />
    </ProtectedApp>
  );
}
