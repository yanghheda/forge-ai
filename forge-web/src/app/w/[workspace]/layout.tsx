import type { ReactNode } from "react";

import { ProtectedWorkspace } from "@/features/auth";

export default async function WorkspaceLayout({ children, params }: { children: ReactNode; params: Promise<{ workspace: string }> }) {
  const { workspace } = await params;
  return <ProtectedWorkspace workspace={workspace}>{children}</ProtectedWorkspace>;
}
