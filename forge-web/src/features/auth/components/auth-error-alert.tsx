import { Alert } from "@arco-design/web-react";

import { isApiError } from "@/lib/api";

export function AuthErrorAlert({ error, login = false }: { error: unknown; login?: boolean }) {
  if (!error) return null;
  const invalidCredentials = login && isApiError(error) && error.code === "UNAUTHENTICATED";
  const securityRejected = isApiError(error) && ["CSRF_REJECTED", "ORIGIN_REJECTED"].includes(error.code);
  const message = invalidCredentials
    ? "账号不存在或密码错误，请重新输入。"
    : securityRejected
      ? "请求安全校验失败，请刷新页面后重试。"
      : isApiError(error) ? error.message : "请求失败，请稍后重试。";
  const requestId = !invalidCredentials && isApiError(error) && error.requestId ? ` 请求编号：${error.requestId}` : "";
  return <Alert type="error" content={`${message}${requestId}`} />;
}
