"use client";

import { Button, Card, Message, Spin, Typography } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";

import { formatRequestError, isApiError } from "@/lib/api";
import { getCurrentUser, logout } from "../api/auth-api";
import { AuthErrorAlert } from "./auth-error-alert";

export function ProtectedApp({ children }: { children: ReactNode }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const currentUser = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser(), retry: false });
  const logoutMutation = useMutation({ mutationFn: () => logout(), onSuccess: () => { queryClient.clear(); router.replace("/login"); }, onError: (error) => Message.error(formatRequestError(error)) });

  useEffect(() => {
    if (currentUser.isError && isApiError(currentUser.error) && currentUser.error.status === 401) router.replace("/login");
  }, [currentUser.error, currentUser.isError, router]);

  if (currentUser.isPending) return <Spin tip="正在验证登录状态…" />;
  if (currentUser.isError) return <Card title="无法进入工作台"><AuthErrorAlert error={currentUser.error} /></Card>;

  return <>
    <div style={{ display: "flex", justifyContent: "flex-end", alignItems: "center", gap: 8, marginBottom: 18 }}>
      <Typography.Text type="secondary">{currentUser.data.displayName}</Typography.Text>
      <Button type="text" size="small" loading={logoutMutation.isPending} onClick={() => logoutMutation.mutate()}>退出</Button>
    </div>
    {children}
  </>;
}
