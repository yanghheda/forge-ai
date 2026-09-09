import { notFound } from "next/navigation";

import { ProtectedApp } from "@/features/auth";
import { OrganizationRequirementDetail } from "@/features/work-item";

export default async function RequirementPage({ params }: { params: Promise<{ id: string }> }) {
  const id = Number((await params).id);
  if (!Number.isSafeInteger(id) || id <= 0) notFound();
  return <ProtectedApp><OrganizationRequirementDetail requirementId={id} /></ProtectedApp>;
}
