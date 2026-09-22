import { useParams } from "react-router";

import { ProtectedApp } from "@/features/auth";
import { RequirementContext } from "@/features/console";
import { PrdWorkspace } from "@/features/document";

import { NotFoundView } from "./not-found-view";

export function RequirementDocumentsView() {
  const id = Number(useParams().id);
  if (!Number.isSafeInteger(id) || id <= 0) return <NotFoundView />;
  return (
    <ProtectedApp>
      <div style={{ maxWidth: 1440, margin: "0 auto", padding: 20 }}>
        <RequirementContext active="document" requirementId={String(id)} />
        <PrdWorkspace requirementId={id} />
      </div>
    </ProtectedApp>
  );
}
