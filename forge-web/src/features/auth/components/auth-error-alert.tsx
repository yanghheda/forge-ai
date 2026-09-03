import { Alert } from "@arco-design/web-react";

import { isApiError } from "@/lib/api";

export function AuthErrorAlert({ error }: { error: unknown }) {
  if (!error) return null;
  const securityRejected = isApiError(error) && ["CSRF_REJECTED", "ORIGIN_REJECTED"].includes(error.code);
  const message = securityRejected ? "请求安全校验失败，请刷新页面后重试。" : isApiError(error) ? error.message : "请求失败，请稍后重试。";
  const requestId = isApiError(error) && error.requestId ? ` 请求编号：${error.requestId}` : "";
  return <Alert type="error" content={`${message}${requestId}`} />;
}
