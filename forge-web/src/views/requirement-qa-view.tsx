import { useParams } from "react-router";

import { ProtectedApp } from "@/features/auth";
import { DeliveryScreen } from "@/features/console";

export function RequirementQaView() {
  return (
    <ProtectedApp>
      <DeliveryScreen kind="qa" requirementId={useParams().id} />
    </ProtectedApp>
  );
}
