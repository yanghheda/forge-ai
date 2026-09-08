import { notFound } from "next/navigation";
import { UxTaskRoute } from "@/features/work-item";

export default async function Page({
  params,
}: {
  params: Promise<{ workspace: string; project: string; id: string }>;
}) {
  const route = await params;
  const id = Number(route.id);
  if (!Number.isSafeInteger(id) || id <= 0) notFound();
  return <UxTaskRoute workspaceSlug={route.workspace} projectKey={route.project} workItemId={id} />;
}
