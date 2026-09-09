"use client";

import { Alert, Spin } from "@arco-design/web-react";
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";

import { getCurrentUser } from "@/features/auth";
import { formatRequestError } from "@/lib/api";

import { listProjects } from "../api/project-api";

export function ProjectAccessGuard({
  workspaceSlug,
  projectKey,
  children,
}: {
  workspaceSlug: string;
  projectKey: string;
  children: ReactNode;
}) {
  const router = useRouter();
  const currentUser = useQuery({
    queryKey: ["current-user"],
    queryFn: () => getCurrentUser(),
    retry: false,
  });
  const workspace = currentUser.data?.workspaces.find(
    (item) => item.slug === workspaceSlug,
  );
  const projects = useQuery({
    queryKey: ["projects", workspace?.id],
    queryFn: () => listProjects(workspace!.id),
    enabled: workspace !== undefined,
    retry: false,
  });
  const project = projects.data?.find((item) => item.key === projectKey);
  const accessDenied =
    currentUser.isSuccess &&
    (workspace === undefined || (projects.isSuccess && project === undefined));

  useEffect(() => {
    if (accessDenied) router.replace(`/w/${workspaceSlug}`);
  }, [accessDenied, router, workspaceSlug]);

  if (currentUser.isError || projects.isError) {
    return <Alert type="error" content={formatRequestError(currentUser.error ?? projects.error)} />;
  }
  if (accessDenied) return <Spin tip="正在返回首页…" />;
  if (currentUser.isPending || projects.isPending) {
    return <Spin tip="正在验证项目访问权限…" />;
  }
  return project ? children : null;
}
