import { useParams } from "react-router";

import { ProtectedApp } from "@/features/auth";
import { DeliveryScreen } from "@/features/console";

export function RequirementReleaseView() {
  return (
    <ProtectedApp>
      <DeliveryScreen kind="release" requirementId={useParams().id} />
    </ProtectedApp>
  );
}
