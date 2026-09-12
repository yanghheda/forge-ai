import { ProtectedApp } from "@/features/auth";
import { RequirementContext } from "@/features/console";
import { PrdWorkspace } from "@/features/document";
import { notFound } from "next/navigation";

export default async function DocumentPage({ params }: { params: Promise<{ id: string }> }) {
  const id = Number((await params).id);
  if (!Number.isSafeInteger(id) || id <= 0) notFound();
  return (
    <ProtectedApp>
      <div style={{ maxWidth: 1600, margin: "0 auto", padding: 20 }}>
        <RequirementContext active="document" requirementId={String(id)} />
        <PrdWorkspace requirementId={id} />
      </div>
    </ProtectedApp>
  );
}
