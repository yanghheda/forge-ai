import { useParams } from "react-router";

import { ProtectedApp } from "@/features/auth";
import { DeliveryScreen } from "@/features/console";

export function RequirementUxView() {
  return (
    <ProtectedApp>
      <DeliveryScreen kind="ux" requirementId={useParams().id} />
    </ProtectedApp>
  );
}
