"use client";

import { Card, Spin } from "@arco-design/web-react";
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";

import { isApiError } from "@/lib/api";
import { getCurrentUser } from "../api/auth-api";
import { AuthErrorAlert } from "./auth-error-alert";

export function ProtectedApp({ children }: { children: ReactNode }) {
  const router = useRouter();
  const currentUser = useQuery({
    queryKey: ["current-user"],
    queryFn: () => getCurrentUser(),
    retry: false,
  });

  useEffect(() => {
    if (currentUser.isError && isApiError(currentUser.error) && currentUser.error.status === 401) router.replace("/login");
  }, [currentUser.error, currentUser.isError, router]);

  if (currentUser.isPending) return <Spin tip="正在验证登录状态…" />;
  if (currentUser.isError)
    return (
      <Card title="无法进入工作台">
        <AuthErrorAlert error={currentUser.error} />
      </Card>
    );

  return children;
}
