import { notFound } from "next/navigation";

import { ProtectedApp } from "@/features/auth";
import { RequirementContext } from "@/features/console";
import { OrganizationRequirementDetail } from "@/features/work-item";

export default async function RequirementPage({ params }: { params: Promise<{ id: string }> }) {
  const id = Number((await params).id);
  if (!Number.isSafeInteger(id) || id <= 0) notFound();
  return <ProtectedApp><div style={{ maxWidth: 1440, margin: "0 auto", padding: 20 }}><RequirementContext active="detail" requirementId={String(id)} /><OrganizationRequirementDetail requirementId={id} /></div></ProtectedApp>;
}
