import { Alert } from "@arco-design/web-react";

import { formatRequestError, isApiError } from "@/lib/api";
import { CSSProperties } from "react";

export function AuthErrorAlert({ error, login = false, style }: { error: unknown; login?: boolean; style?: CSSProperties }) {
  if (!error) return null;
  const invalidCredentials = login && isApiError(error) && error.code === "UNAUTHENTICATED";
  const content = invalidCredentials ? "账号不存在或密码错误，请重新输入。" : formatRequestError(error);
  return <Alert type="error" content={content} style={style} />;
}
