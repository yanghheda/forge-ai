import { notFound } from "next/navigation";
import { RequirementRoute } from "@/features/work-item";

export default async function Page({
  params,
}: {
  params: Promise<{ workspace: string; project: string; id: string }>;
}) {
  const route = await params;
  const id = Number(route.id);
  if (!Number.isSafeInteger(id) || id <= 0) notFound();
  return (
    <RequirementRoute
      workspaceSlug={route.workspace}
      projectKey={route.project}
      workItemId={id}
    />
  );
}
