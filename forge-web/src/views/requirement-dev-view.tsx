import { useParams } from "react-router";

import { ProtectedApp } from "@/features/auth";
import { DeliveryScreen } from "@/features/console";

export function RequirementDevView() {
  return (
    <ProtectedApp>
      <DeliveryScreen kind="development" requirementId={useParams().id} />
    </ProtectedApp>
  );
}
