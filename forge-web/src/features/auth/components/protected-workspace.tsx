"use client";

import { Alert, Button, Card, Spin, Typography } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useEffect, type ReactNode } from "react";

import { isApiError } from "@/lib/api";
import { getCurrentUser, logout } from "../api/auth-api";
import { AuthErrorAlert } from "./auth-error-alert";

export function ProtectedWorkspace({ workspace, children }: { workspace: string; children: ReactNode }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const currentUser = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser(), retry: false });
  const logoutMutation = useMutation({ mutationFn: () => logout(), onSuccess: () => { queryClient.clear(); router.replace("/login"); } });

  useEffect(() => {
    if (currentUser.isError && isApiError(currentUser.error) && currentUser.error.status === 401) router.replace("/login");
  }, [currentUser.error, currentUser.isError, router]);

  if (currentUser.isPending) return <Spin tip="正在验证登录状态…" />;
  if (currentUser.isError) return <Card title="无法进入工作台"><AuthErrorAlert error={currentUser.error} /></Card>;
  const access = currentUser.data.workspaces.find((item) => item.slug === workspace);
  if (!access) return <Alert type="error" content="当前账户无权访问此 Workspace。" />;

  return <section>
    <Card size="small">
      <Typography.Text>{currentUser.data.displayName} · {access.name} · {access.roles.join(", ")}</Typography.Text>
      <Link href={`/w/${workspace}/settings/members`}>成员设置</Link>
      <Link href={`/w/${workspace}/settings/gitlab`}>GitLab 设置</Link>
      <Button type="text" loading={logoutMutation.isPending} onClick={() => logoutMutation.mutate()}>退出</Button>
      <AuthErrorAlert error={logoutMutation.error} />
    </Card>
    {children}
  </section>;
}
